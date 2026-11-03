package com.job.bootstrap.agent.react;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.job.bootstrap.ai.turn.AiChatMessage;
import com.job.bootstrap.ai.turn.AiChatTurnRequest;
import com.job.bootstrap.ai.turn.AiChatTurnResponse;
import com.job.bootstrap.ai.turn.AiToolCall;
import com.job.bootstrap.service.AiModelGatewayService;
import com.job.enums.AiModelTier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 功能: 通用 ReAct 执行器（第一阶段内核）。
 *
 * 职责边界（与 Workflow 分工）:
 * 1. 本类只负责“在预算与白名单内让模型自主决策”：调模型 → 执行允许的工具 → 回填 Observation → 再决策；
 * 2. **不做**身份/权限/业务条件/最终结果验真——这些是 Workflow 的职责；
 * 3. 终结工具只返回“提议”（AgentResult），Workflow 必须回到真实工具结果复核后才能对外输出。
 *
 * 协议约束:
 * 1. content 为空但 tool_calls 有值是合法响应；
 * 2. assistant 工具调用与 tool 结果按 toolCallId 严格配对，暂停/结束也补齐配对消息；
 * 3. 终结工具必须单独成轮（与业务工具混用返回协议错误并要求修正）；
 * 4. 非法终结参数给一次修正机会，再次非法按协议失败退出；
 * 5. 相同查询结果无变化时禁止循环（第二次重复直接失败）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReActRunner {

    /** 协议修正机会（截断/空响应/混用/重复查询）。 */
    private static final int MAX_PROTOCOL_CORRECTIONS = 1;
    /** 终结参数修正机会。 */
    private static final int MAX_TERMINAL_CORRECTIONS = 1;

    private static final String SCENE_AGENT_REACT = "AGENT_REACT";

    private final AiModelGatewayService aiModelGatewayService;
    private final TerminalToolContract terminalToolContract;
    private final ObjectMapper objectMapper;

    /**
     * 业务工具执行器：由调用方（Workflow）提供——负责参数校验、权限、条件合并、真实执行与结果保存。
     */
    @FunctionalInterface
    public interface ToolExecutor {
        /**
         * @param javaToolName Java 工具名（ClassName.methodName）
         * @param arguments    模型生成的参数（已解析为 Map，仍需执行器校验）
         * @return 工具结果 JSON（写入 Observation）
         */
        String execute(String javaToolName, Map<String, Object> arguments) throws Exception;
    }

    /**
     * 执行一次 ReAct run。
     */
    public AgentResult run(
            AgentDefinition definition,
            AgentRunContext context,
            ToolExecutor toolExecutor,
            Long userId,
            String traceId
    ) {
        int protocolCorrections = 0;
        int terminalCorrections = 0;
        Map<String, String> executedSignatures = new LinkedHashMap<>();

        while (true) {
            if (context.deadlineExceeded(definition)) {
                return AgentResult.failed(AgentResult.ReasonCode.BUDGET_TIMEOUT,
                        "已超过总时限 " + definition.deadlineSeconds() + " 秒",
                        context.toolCalls(), context.turn(), context.elapsedMillis());
            }
            if (context.turnsExhausted(definition)) {
                return AgentResult.failed(AgentResult.ReasonCode.BUDGET_TURNS_EXHAUSTED,
                        "模型轮次已用尽（" + definition.maxTurns() + " 轮）",
                        context.toolCalls(), context.turn(), context.elapsedMillis());
            }

            context.nextTurn();
            AiChatTurnResponse response;
            try {
                response = aiModelGatewayService.chatTurn(
                        SCENE_AGENT_REACT,
                        AiModelTier.STANDARD,
                        AiChatTurnRequest.of(context.messages(), definition.toolSpecs()),
                        userId,
                        traceId
                );
            } catch (Exception exception) {
                log.warn("ReAct 模型调用失败，agent={}, turn={}, error={}",
                        definition.agentName(), context.turn(), exception.getMessage());
                return AgentResult.failed(AgentResult.ReasonCode.MODEL_ERROR,
                        "模型调用失败：" + exception.getMessage(),
                        context.toolCalls(), context.turn(), context.elapsedMillis());
            }

            // 1. 输出截断：给一次修正机会，再次截断按协议失败（截断的参数不可信）
            if (response.truncated()) {
                context.append(AiChatMessage.assistant(response.content(), null));
                if (++protocolCorrections > MAX_PROTOCOL_CORRECTIONS) {
                    return AgentResult.failed(AgentResult.ReasonCode.PROTOCOL_ERROR,
                            "模型输出连续被截断", context.toolCalls(), context.turn(), context.elapsedMillis());
                }
                context.append(AiChatMessage.user("上一轮输出被截断。请精简内容后重新提交工具调用或终结结果。"));
                continue;
            }

            // 2. 既没有工具调用也没有终结：协议错误，提示必须用终结工具提交结果
            if (!response.hasToolCalls()) {
                context.append(AiChatMessage.assistant(response.content(), null));
                if (++protocolCorrections > MAX_PROTOCOL_CORRECTIONS) {
                    return AgentResult.failed(AgentResult.ReasonCode.PROTOCOL_ERROR,
                            "模型未通过终结工具提交结果", context.toolCalls(), context.turn(), context.elapsedMillis());
                }
                context.append(AiChatMessage.user(
                        "请通过 " + definition.terminalToolName() + " 工具提交结构化结果，不要只用文本回答。"));
                continue;
            }

            // 3. 先完整写入 assistant 消息（含全部 toolCalls），保证消息序列配对
            context.append(AiChatMessage.assistant(response.content(), response.toolCalls()));

            boolean hasTerminal = response.toolCalls().stream()
                    .anyMatch(call -> definition.terminalToolName().equals(call.name()));

            // 4. 终结工具必须单独成轮
            if (hasTerminal && response.toolCalls().size() > 1) {
                for (AiToolCall call : response.toolCalls()) {
                    context.append(AiChatMessage.tool(call.id(),
                            protocolError("终结工具不能与业务工具在同一轮提交，请先完成查询，再单独提交终结结果。")));
                }
                if (++protocolCorrections > MAX_PROTOCOL_CORRECTIONS) {
                    return AgentResult.failed(AgentResult.ReasonCode.PROTOCOL_ERROR,
                            "终结工具与业务工具多次混用", context.toolCalls(), context.turn(), context.elapsedMillis());
                }
                context.append(AiChatMessage.user("请把终结结果放到单独一轮提交，不要与业务查询混合。"));
                continue;
            }

            // 5. 终结结果：解析 + 业务约束第一层校验（候选真伪留待 Workflow 复核）
            if (hasTerminal) {
                AiToolCall call = response.toolCalls().get(0);
                TerminalToolContract.ParseResult parsed = terminalToolContract.parse(call.argumentsJson());
                if (parsed.error() != null) {
                    context.append(AiChatMessage.tool(call.id(), protocolError(parsed.error())));
                    if (++terminalCorrections > MAX_TERMINAL_CORRECTIONS) {
                        return AgentResult.failed(AgentResult.ReasonCode.PROTOCOL_ERROR,
                                "终结结果连续非法：" + parsed.error(),
                                context.toolCalls(), context.turn(), context.elapsedMillis());
                    }
                    context.append(AiChatMessage.user("终结参数不合法：" + parsed.error() + " 请修正后重新提交。"));
                    continue;
                }
                // 文档要求：结束时也要为终结工具保存配对的 tool 消息
                context.append(AiChatMessage.tool(call.id(), "{\"accepted\":true,\"note\":\"待工作流复核\"}"));

                TerminalToolContract.TerminalProposal proposal = parsed.proposal();
                return new AgentResult(
                        AgentResult.Outcome.valueOf(proposal.outcome()),
                        proposal.candidateIds(),
                        proposal.missingFields(),
                        proposal.question(),
                        proposal.reasonCode(),
                        proposal.summary(),
                        context.toolCalls(),
                        context.turn(),
                        context.elapsedMillis()
                );
            }

            // 6. 业务工具：串行执行，越权/非法参数/预算耗尽都在这里处理
            boolean budgetExhausted = false;
            for (AiToolCall call : response.toolCalls()) {
                if (!definition.isAllowed(call.name())) {
                    context.append(AiChatMessage.tool(call.id(),
                            protocolError("工具不在白名单：" + call.name())));
                    continue;
                }
                if (context.toolCallsExhausted(definition)) {
                    context.append(AiChatMessage.tool(call.id(),
                            protocolError("业务工具调用预算已用尽（" + definition.maxToolCalls() + " 次）")));
                    budgetExhausted = true;
                    continue;
                }

                Map<String, Object> arguments;
                try {
                    arguments = StringUtils.hasText(call.argumentsJson())
                            ? objectMapper.readValue(call.argumentsJson(), Map.class)
                            : Map.of();
                } catch (Exception exception) {
                    context.append(AiChatMessage.tool(call.id(), protocolError("参数不是合法 JSON")));
                    continue;
                }

                // 重复查询熔断：相同工具+参数且结果无变化，第二次直接失败（禁止循环）
                String signature = call.name() + "|" + String.valueOf(arguments);
                if (executedSignatures.containsKey(signature)) {
                    context.append(AiChatMessage.tool(call.id(),
                            protocolError("相同查询已执行过且结果无变化，请调整关键词/页码或提交终结结果。")));
                    return AgentResult.failed(AgentResult.ReasonCode.PROTOCOL_ERROR,
                            "重复相同查询且结果无变化", context.toolCalls(), context.turn(), context.elapsedMillis());
                }

                context.countToolCall();
                String javaToolName = definition.javaToolName(call.name());
                try {
                    String result = toolExecutor.execute(javaToolName, arguments);
                    executedSignatures.put(signature, result);
                    context.append(AiChatMessage.tool(call.id(), result));
                } catch (Exception exception) {
                    log.info("ReAct 工具执行失败，tool={}, error={}", javaToolName, exception.getMessage());
                    context.append(AiChatMessage.tool(call.id(), protocolError("工具执行失败：" + exception.getMessage())));
                }
            }
            if (budgetExhausted) {
                return AgentResult.failed(AgentResult.ReasonCode.BUDGET_TOOL_CALLS_EXHAUSTED,
                        "业务工具调用次数已用尽（" + definition.maxToolCalls() + " 次）",
                        context.toolCalls(), context.turn(), context.elapsedMillis());
            }
        }
    }

    // 协议错误结果：作为 tool 消息回填，让模型看到并修正（不是抛异常）
    private String protocolError(String message) {
        try {
            return objectMapper.writeValueAsString(Map.of("error", message, "protocolError", true));
        } catch (Exception exception) {
            return "{\"error\":\"协议错误\",\"protocolError\":true}";
        }
    }
}
