package com.job.bootstrap.agent.plan;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.job.bootstrap.agent.schema.AgentToolSchemaRegistry;
import com.job.bootstrap.service.AiModelGatewayService;
import com.job.common.agent.tool.AgentToolParamSchema;
import com.job.common.agent.tool.AgentToolSchema;
import com.job.enums.AiModelTier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 功能: 计划重规划器（企业级对标 G2：Replan/反思循环）。
 *
 * 设计说明:
 * 1. 执行器某步骤失败时调用本组件：基于「用户目标 + 已完成步骤摘要 + 失败步骤与原因」
 *    让 LLM 给出替代步骤（可引用已完成步骤的 {{step_N.output.xxx}} 输出），失败旧步骤随之放弃。
 * 2. 复用 AgentPlanAugmenter 模式：走动态模型网关 PLAN_REPLAN 场景，输出受 Schema 约束的 JSON。
 * 3. 安全边界（不信任模型输出）：toolName 必须在 {@link AgentToolSchemaRegistry} 白名单内、
 *    toolInput 的 key 必须是该工具入参；非法步骤一律丢弃，并限制替代步骤数量上限。
 * 4. 任何异常（场景未配置、模型失败、JSON 非法）都降级为空列表，执行器按原行为失败终止，绝不卡主流程。
 * 5. 触发由执行器控制（步骤失败 + replan 预算未用尽），本组件只负责“给出替代步骤”。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentReplanner {

    private static final String AI_SCENE_PLAN_REPLAN = "PLAN_REPLAN";
    /** 单次重规划最多给出的替代步骤数量，防止模型失控堆步骤。 */
    private static final int MAX_REPLAN_STEPS = 3;

    private final AiModelGatewayService aiModelGatewayService;
    private final AgentToolSchemaRegistry agentToolSchemaRegistry;
    private final ObjectMapper objectMapper;

    /**
     * 一条通过校验的重规划替代步骤。
     *
     * @param stepName          步骤名
     * @param stepGoal          步骤目标
     * @param toolName          标准工具名（白名单内）
     * @param toolInput         该步可执行参数（可含 {{step_N.output.xxx}} 占位符）
     * @param completionCriteria 完成条件
     */
    public record ReplanStep(
            String stepName,
            String stepGoal,
            String toolName,
            Map<String, Object> toolInput,
            String completionCriteria
    ) {
    }

    /**
     * 基于失败上下文生成替代步骤。
     *
     * @param userId               用户 ID
     * @param traceId              链路 ID
     * @param userGoal             用户原始目标
     * @param completedStepsSummary 已完成步骤摘要（含 stepNo，供占位符引用）
     * @param failedStepName       失败步骤名
     * @param failedError          失败原因
     * @return 通过白名单与入参校验的替代步骤；无则空列表
     */
    public List<ReplanStep> replan(
            Long userId,
            String traceId,
            String userGoal,
            String completedStepsSummary,
            String failedStepName,
            String failedError
    ) {
        if (!StringUtils.hasText(userGoal)) {
            return List.of();
        }
        try {
            String output = aiModelGatewayService.chat(
                    AI_SCENE_PLAN_REPLAN,
                    AiModelTier.STANDARD, // 重规划是规划级决策，走标准档
                    buildVariables(),
                    buildUserMessage(userGoal.trim(), completedStepsSummary, failedStepName, failedError),
                    userId,
                    traceId
            );
            return parseSteps(output);
        } catch (Exception exception) {
            log.debug("LLM 重规划降级为空，userId={}, traceId={}, error={}", userId, traceId, exception.getMessage());
            return List.of();
        }
    }

    // 只把白名单工具（名 + 入参 key）透给模型，并要求用 {{step_N.output.xxx}} 引用已完成步骤输出
    private Map<String, Object> buildVariables() {
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("allowedTools", agentToolSchemaRegistry.listAll().stream().map(schema -> {
            Map<String, Object> tool = new LinkedHashMap<>();
            tool.put("toolName", schema.getToolName());
            tool.put("inputParams", schema.getInputParams() == null ? List.of()
                    : schema.getInputParams().stream().map(AgentToolParamSchema::getName).toList());
            return tool;
        }).toList());
        variables.put("jsonSchema", """
                [
                  {
                    "stepName": "步骤名",
                    "toolName": "JobSearchTool.searchJobs",
                    "toolInput": {"keyword": "Java"},
                    "completionCriteria": "完成条件"
                  }
                ]
                """);
        return variables;
    }

    private String buildUserMessage(String userGoal, String completedStepsSummary, String failedStepName, String failedError) {
        return """
                你是求职 Agent 的重规划器。某一步骤执行失败，请在保留已完成步骤结果的前提下，给出完成用户目标的替代步骤。

                规则:
                1. 只输出 JSON 数组，不要解释、Markdown 或多余文本。
                2. toolName 只能是给定白名单工具之一，toolInput 的 key 只能是对应工具的入参。
                3. 需要引用已完成步骤的输出时，用 {{step_N.output.xxx}} 占位符（N 是已完成步骤的序号）。
                4. 失败的旧步骤会被放弃，你的步骤要能独立推进用户目标；没有可行替代就输出 []。

                用户目标:
                """ + "\n" + userGoal
                + "\n\n已完成的步骤:\n" + (StringUtils.hasText(completedStepsSummary) ? completedStepsSummary : "（无）")
                + "\n失败的步骤: " + (StringUtils.hasText(failedStepName) ? failedStepName : "（未知）")
                + "\n失败原因: " + (StringUtils.hasText(failedError) ? failedError : "（未知）");
    }

    // 解析并校验：白名单工具 + 入参 key 合法 + 数量上限；非法步骤丢弃
    private List<ReplanStep> parseSteps(String output) {
        String json = stripJsonArray(output);
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            List<Map<String, Object>> rows = objectMapper.readValue(json, new TypeReference<>() {
            });
            List<ReplanStep> steps = new ArrayList<>();
            for (Map<String, Object> row : rows) {
                ReplanStep step = toStep(row);
                if (step != null) {
                    steps.add(step);
                }
                if (steps.size() >= MAX_REPLAN_STEPS) {
                    break;
                }
            }
            return steps;
        } catch (Exception exception) {
            log.debug("LLM 重规划 JSON 解析失败，output={}, error={}", output, exception.getMessage());
            return List.of();
        }
    }

    // 校验单条：toolName 必须在 Schema 白名单、toolInput key 必须是该工具入参；否则返回 null
    private ReplanStep toStep(Map<String, Object> row) {
        String toolName = text(row.get("toolName"));
        if (!StringUtils.hasText(toolName)) {
            return null;
        }
        AgentToolSchema schema = agentToolSchemaRegistry.find(toolName.trim()).orElse(null);
        if (schema == null) {
            return null;
        }
        Map<String, Object> toolInput = readToolInput(row.get("toolInput"));
        if (!inputKeysAllowed(toolInput, schema)) {
            return null;
        }
        return new ReplanStep(
                text(row.get("stepName")),
                text(row.get("stepGoal")),
                schema.getToolName(),
                toolInput,
                text(row.get("completionCriteria"))
        );
    }

    private boolean inputKeysAllowed(Map<String, Object> toolInput, AgentToolSchema schema) {
        if (toolInput.isEmpty()) {
            return true;
        }
        Set<String> allowed = schema.getInputParams() == null ? Set.of()
                : schema.getInputParams().stream().map(AgentToolParamSchema::getName).collect(Collectors.toSet());
        return allowed.containsAll(toolInput.keySet());
    }

    private Map<String, Object> readToolInput(Object value) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (value instanceof Map<?, ?> map) {
            map.forEach((k, v) -> result.put(String.valueOf(k), v));
        }
        return result;
    }

    private String text(Object value) {
        return value == null ? null : String.valueOf(value).trim();
    }

    // 去掉 ```json 围栏，截取第一个 [ 到最后一个 ] 之间的 JSON 数组
    private String stripJsonArray(String output) {
        if (!StringUtils.hasText(output)) {
            return "";
        }
        String t = output.trim();
        if (t.startsWith("```")) {
            t = t.replaceFirst("(?s)^```(?:json)?\\s*", "");
            t = t.replaceFirst("(?s)\\s*```$", "");
        }
        int start = t.indexOf('[');
        int end = t.lastIndexOf(']');
        if (start >= 0 && end > start) {
            return t.substring(start, end + 1);
        }
        return t;
    }
}
