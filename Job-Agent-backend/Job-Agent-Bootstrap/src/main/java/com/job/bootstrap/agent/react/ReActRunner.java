package com.job.bootstrap.agent.react;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.job.bootstrap.agent.task.TaskBudgetContext;
import com.job.bootstrap.agent.task.TaskExecutionBudget;
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

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 功能: 通用 ReAct 执行器（第二阶段泛化版）。
 *
 * 职责边界（与 Workflow 分工）:
 * 1. 本类只负责“在预算与白名单内让模型自主决策”：调模型 → 执行允许的工具 → 回填 Observation → 再决策；
 * 2. **不做**身份/权限/业务条件/最终结果验真——这些是 Workflow 的职责；
 * 3. 终结工具只返回“提议”，Workflow 必须回到真实工具结果复核后才能对外输出。
 *
 * 第二阶段泛化:
 * 1. 工具反馈循环、配对消息、检查点、预算、错误处理**共用一份**；
 * 2. 每个 Agent 提供自己的 {@link TerminalContract}（工具定义 + 第一层校验 + 结论提取），
 *    搜索类继续走旧 {@link #run} 并保持兼容；
 * 3. 业务结论（outcome）与运行时原因码（reasonCode）分离，互不冒充。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReActRunner {

    /** 协议修正机会（截断/空响应/混用/重复查询/ID 非法）。 */
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
         * @param javaToolName Java 工具名或节点内能力名
         * @param arguments    模型生成的参数（已解析为 Map，仍需执行器校验）
         * @return 工具结果 JSON（写入 Observation）
         */
        String execute(String javaToolName, Map<String, Object> arguments) throws Exception;
    }

    /**
     * 通用运行结果：成功时 payload 非空、outcome 来自终结契约；失败时 reasonCode 非空。
     */
    public record ReActRunResult<T>(
            String outcome,
            T payload,
            String reasonCode,
            String summary,
            int toolCallsUsed,
            int turnsUsed,
            long elapsedMillis
    ) {

        public boolean succeeded() {
            return payload != null && reasonCode == null;
        }

        static <T> ReActRunResult<T> failure(String reasonCode, String summary,
                                             int toolCalls, int turns, long elapsed) {
            return new ReActRunResult<>(null, null, reasonCode, summary, toolCalls, turns, elapsed);
        }
    }

    /**
     * 搜索类兼容入口：使用 finish_job_search 契约，返回旧结构。
     */
    public AgentResult run(AgentDefinition definition, AgentRunContext context,
                           ToolExecutor toolExecutor, Long userId, String traceId) {
        ReActRunResult<TerminalToolContract.TerminalProposal> result =
                runWithTerminal(definition, context, toolExecutor, userId, traceId, terminalToolContract);
        if (!result.succeeded()) {
            return AgentResult.failed(result.reasonCode(), result.summary(),
                    result.toolCallsUsed(), result.turnsUsed(), result.elapsedMillis());
        }
        TerminalToolContract.TerminalProposal proposal = result.payload();
        return new AgentResult(
                AgentResult.Outcome.valueOf(proposal.outcome()),
                proposal.candidateIds(),
                proposal.missingFields(),
                proposal.question(),
                proposal.reasonCode(),
                proposal.summary(),
                result.toolCallsUsed(),
                result.turnsUsed(),
                result.elapsedMillis()
        );
    }

    /**
     * 通用执行入口（第二阶段）：任意 Agent 通过自己的 TerminalContract 提交结构化结论。
     */
    public <T> ReActRunResult<T> runWithTerminal(AgentDefinition definition,
                                                 AgentRunContext context,
                                                 ToolExecutor toolExecutor,
                                                 Long userId,
                                                 String traceId,
                                                 TerminalContract<T> terminal) {
        int protocolCorrections = 0;
        int terminalCorrections = 0;
        Map<String, String> executedSignatures = new LinkedHashMap<>();

        while (true) {
            // 父任务取消：不再开始新轮次（第三阶段）
            if (AgentRunExecutionHooks.cancelled()) {
                return ReActRunResult.failure(AgentResult.ReasonCode.TASK_CANCELLED,
                        "任务已被取消", context.toolCalls(), context.turn(), context.elapsedMillis());
            }
            if (deadlineExceeded(definition, context)) {
                return ReActRunResult.failure(AgentResult.ReasonCode.BUDGET_TIMEOUT,
                        "已超过总时限 " + definition.deadlineSeconds() + " 秒",
                        context.toolCalls(), context.turn(), context.elapsedMillis());
            }
            if (context.turnsExhausted(definition)) {
                return ReActRunResult.failure(AgentResult.ReasonCode.BUDGET_TURNS_EXHAUSTED,
                        "模型轮次已用尽（" + definition.maxTurns() + " 轮）",
                        context.toolCalls(), context.turn(), context.elapsedMillis());
            }

            /*
             * 审查 F1：Runner 不再扣模型额度——额度由网关在**每次真实 HTTP 尝试**前扣，
             * 否则一次正常请求会被扣两次（Runner + 网关）。这里只做预算闸门与并发闸门。
             */
            TaskExecutionBudget budget = AgentRunExecutionHooks.budget();
            if (budget != null && !budget.canContinue()) {
                String reason = budget.isCancelled()
                        ? AgentResult.ReasonCode.TASK_CANCELLED : AgentResult.ReasonCode.BUDGET_TIMEOUT;
                return ReActRunResult.failure(reason, "父任务预算已结束",
                        context.toolCalls(), context.turn(), context.elapsedMillis());
            }
            boolean concurrencyEntered = budget != null && budget.tryEnterModelCall();
            if (budget != null && !concurrencyEntered) {
                return ReActRunResult.failure(AgentResult.ReasonCode.BUDGET_MODEL_CALLS_EXHAUSTED,
                        "父任务模型并发已达上限（" + budget.modelCallsUsed() + " 次已用）",
                        context.toolCalls(), context.turn(), context.elapsedMillis());
            }

            context.nextTurn();
            AiChatTurnResponse response;
            try {
                /*
                 * 第三阶段收口：把父预算放进线程上下文——网关内部的重试/备用模型
                 * 会用同一截止时间与请求额度，不能只在 Runner 外层扣一次额度。
                 */
                TaskBudgetContext.set(budget);
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
                return ReActRunResult.failure(AgentResult.ReasonCode.MODEL_ERROR,
                        "模型调用失败：" + exception.getMessage(),
                        context.toolCalls(), context.turn(), context.elapsedMillis());
            } finally {
                TaskBudgetContext.clear();
                if (budget != null) {
                    budget.exitModelCall();
                }
            }

            // R5：模型返回后立即检查截止时间——迟到的成功结果同样不能接受
            if (deadlineExceeded(definition, context)) {
                return ReActRunResult.failure(AgentResult.ReasonCode.BUDGET_TIMEOUT,
                        "模型返回时已超过总时限 " + definition.deadlineSeconds() + " 秒",
                        context.toolCalls(), context.turn(), context.elapsedMillis());
            }

            // 1. 输出截断：一次修正机会
            if (response.truncated()) {
                context.append(AiChatMessage.assistant(response.content(), null));
                if (++protocolCorrections > MAX_PROTOCOL_CORRECTIONS) {
                    return ReActRunResult.failure(AgentResult.ReasonCode.PROTOCOL_ERROR,
                            "模型输出连续被截断", context.toolCalls(), context.turn(), context.elapsedMillis());
                }
                context.append(AiChatMessage.user("上一轮输出被截断。请精简内容后重新提交工具调用或终结结果。"));
                continue;
            }

            // 2. 既没有工具调用也没有终结：提示必须用终结工具
            if (!response.hasToolCalls()) {
                context.append(AiChatMessage.assistant(response.content(), null));
                if (++protocolCorrections > MAX_PROTOCOL_CORRECTIONS) {
                    return ReActRunResult.failure(AgentResult.ReasonCode.PROTOCOL_ERROR,
                            "模型未通过终结工具提交结果", context.toolCalls(), context.turn(), context.elapsedMillis());
                }
                context.append(AiChatMessage.user(
                        "请通过 " + terminal.toolName() + " 工具提交结构化结果，不要只用文本回答。"));
                continue;
            }

            // 3. toolCallId 校验（非空且本轮唯一）
            String idError = validateToolCallIds(response.toolCalls());
            if (idError != null) {
                context.append(AiChatMessage.assistant(response.content(), response.toolCalls()));
                for (AiToolCall call : response.toolCalls()) {
                    context.append(AiChatMessage.tool(call.id(), protocolError(idError)));
                }
                if (++protocolCorrections > MAX_PROTOCOL_CORRECTIONS) {
                    return ReActRunResult.failure(AgentResult.ReasonCode.PROTOCOL_ERROR, idError,
                            context.toolCalls(), context.turn(), context.elapsedMillis());
                }
                context.append(AiChatMessage.user("工具调用 ID 不合法：" + idError + " 请重新提交。"));
                continue;
            }
            // 先完整写入 assistant 消息（含全部 toolCalls），保证消息序列配对
            context.append(AiChatMessage.assistant(response.content(), response.toolCalls()));

            boolean hasTerminal = response.toolCalls().stream()
                    .anyMatch(call -> terminal.toolName().equals(call.name()));

            // 4. 终结工具必须单独成轮
            if (hasTerminal && response.toolCalls().size() > 1) {
                for (AiToolCall call : response.toolCalls()) {
                    context.append(AiChatMessage.tool(call.id(),
                            protocolError("终结工具不能与业务工具在同一轮提交，请先完成查询，再单独提交终结结果。")));
                }
                if (++protocolCorrections > MAX_PROTOCOL_CORRECTIONS) {
                    return ReActRunResult.failure(AgentResult.ReasonCode.PROTOCOL_ERROR,
                            "终结工具与业务工具多次混用", context.toolCalls(), context.turn(), context.elapsedMillis());
                }
                context.append(AiChatMessage.user("请把终结结果放到单独一轮提交，不要与业务查询混合。"));
                continue;
            }

            // 5. 终结结果：解析 + 第一层校验（真伪留待 Workflow 复核）
            if (hasTerminal) {
                AiToolCall call = response.toolCalls().get(0);
                if (deadlineExceeded(definition, context)) {
                    context.append(AiChatMessage.tool(call.id(), protocolError("已超过总时限，结果不再接受")));
                    return ReActRunResult.failure(AgentResult.ReasonCode.BUDGET_TIMEOUT,
                            "终结结果到达时已超过总时限 " + definition.deadlineSeconds() + " 秒",
                            context.toolCalls(), context.turn(), context.elapsedMillis());
                }
                TerminalContract.TerminalParseResult<T> parsed = terminal.parseTyped(call.argumentsJson());
                if (parsed.error() != null) {
                    context.append(AiChatMessage.tool(call.id(), protocolError(parsed.error())));
                    if (++terminalCorrections > MAX_TERMINAL_CORRECTIONS) {
                        return ReActRunResult.failure(AgentResult.ReasonCode.PROTOCOL_ERROR,
                                "终结结果连续非法：" + parsed.error(),
                                context.toolCalls(), context.turn(), context.elapsedMillis());
                    }
                    context.append(AiChatMessage.user("终结参数不合法：" + parsed.error() + " 请修正后重新提交。"));
                    continue;
                }
                // 文档要求：结束时也要为终结工具保存配对的 tool 消息
                context.append(AiChatMessage.tool(call.id(), "{\"accepted\":true,\"note\":\"待工作流复核\"}"));
                AgentRunExecutionHooks.checkpoint(context.messages());

                T proposal = parsed.proposal();
                return new ReActRunResult<>(
                        terminal.outcomeOf(proposal),
                        proposal,
                        null,
                        terminal.summaryOf(proposal),
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

                // 每次工具执行前检查截止时间
                if (deadlineExceeded(definition, context)) {
                    closePendingCalls(context, response.toolCalls(), call, "已超过总时限，未执行");
                    return ReActRunResult.failure(AgentResult.ReasonCode.BUDGET_TIMEOUT,
                            "工具执行前已超过总时限 " + definition.deadlineSeconds() + " 秒",
                            context.toolCalls(), context.turn(), context.elapsedMillis());
                }

                // 重复查询熔断：相同工具+参数且结果无变化，第二次直接失败（禁止循环）
                String signature = call.name() + "|" + arguments;
                if (executedSignatures.containsKey(signature)) {
                    context.append(AiChatMessage.tool(call.id(),
                            protocolError("相同查询已执行过且结果无变化，请调整参数或提交终结结果。")));
                    closePendingCalls(context, response.toolCalls(), null, "已中止，未执行");
                    return ReActRunResult.failure(AgentResult.ReasonCode.PROTOCOL_ERROR,
                            "重复相同查询且结果无变化", context.toolCalls(), context.turn(), context.elapsedMillis());
                }

                // 第三阶段：业务工具额度也要过父预算
                TaskExecutionBudget toolBudget = AgentRunExecutionHooks.budget();
                if (toolBudget != null && !toolBudget.tryReserveToolCall()) {
                    context.append(AiChatMessage.tool(call.id(),
                            protocolError("父任务业务工具额度已用尽（" + toolBudget.maxToolCalls() + " 次）")));
                    closePendingCalls(context, response.toolCalls(), null, "额度用尽，未执行");
                    return ReActRunResult.failure(AgentResult.ReasonCode.BUDGET_TOOL_CALLS_EXHAUSTED,
                            "父任务业务工具额度已用尽", context.toolCalls(), context.turn(), context.elapsedMillis());
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
            // 每轮工具结果落地后写检查点（进程中断也能恢复到已完成的结果）
            AgentRunExecutionHooks.checkpoint(context.messages());

            if (budgetExhausted) {
                closePendingCalls(context, response.toolCalls(), null, "预算已用尽，未执行");
                return ReActRunResult.failure(AgentResult.ReasonCode.BUDGET_TOOL_CALLS_EXHAUSTED,
                        "业务工具调用次数已用尽（" + definition.maxToolCalls() + " 次）",
                        context.toolCalls(), context.turn(), context.elapsedMillis());
            }
        }
    }

    // 统一截止时间判定：definition 的时长 + 外部钩子的绝对截止时间（取更严的）
    private boolean deadlineExceeded(AgentDefinition definition, AgentRunContext context) {
        return context.deadlineExceeded(definition) || AgentRunExecutionHooks.deadlineExceeded();
    }

    // toolCallId 必须非空且本轮唯一
    private String validateToolCallIds(List<AiToolCall> calls) {
        Set<String> ids = new HashSet<>();
        for (AiToolCall call : calls) {
            if (!StringUtils.hasText(call.id())) {
                return "工具调用缺少 toolCallId";
            }
            if (!ids.add(call.id())) {
                return "本轮 toolCallId 重复：" + call.id();
            }
        }
        return null;
    }

    /*
     * 为本轮尚未处理的调用补齐“未执行/已取消”的 tool 消息（配对闭合）。
     */
    private void closePendingCalls(AgentRunContext context, List<AiToolCall> calls,
                                   AiToolCall skip, String reason) {
        Set<String> answered = new LinkedHashSet<>();
        for (AiChatMessage message : context.messages()) {
            if ("tool".equals(message.role()) && message.toolCallId() != null) {
                answered.add(message.toolCallId());
            }
        }
        for (AiToolCall call : calls) {
            if (call.equals(skip) || answered.contains(call.id())) {
                continue;
            }
            context.append(AiChatMessage.tool(call.id(), protocolError(reason)));
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
