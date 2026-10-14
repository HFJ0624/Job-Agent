package com.job.bootstrap.agent.plan;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.job.bootstrap.agent.schema.AgentToolSchemaRegistry;
import com.job.bootstrap.service.AiModelGatewayService;
import com.job.common.agent.tool.AgentToolParamSchema;
import com.job.common.agent.tool.AgentToolSchema;
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
 * 功能: 计划 LLM 补步器（任务5 阶段三）。
 *
 * 设计说明:
 * 1. 在“模板主干”之外，让 LLM 基于用户目标补充少量额外步骤，形成混合计划。
 * 2. 复用 AgentMemoryLlmExtractor 模式：走动态模型网关 PLAN_AUGMENT 场景，输出受 Schema 约束的 JSON。
 * 3. 安全边界（不信任模型输出）：toolName 必须在 {@link AgentToolSchemaRegistry} 白名单内、
 *    toolInput 的 key 必须是该工具入参；非法步骤一律丢弃，并限制补步数量上限。
 * 4. 任何异常（场景未配置、模型失败、JSON 非法）都降级为空列表，计划退化为纯模板，绝不卡主流程。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentPlanAugmenter {

    private static final String AI_SCENE_PLAN_AUGMENT = "PLAN_AUGMENT";
    /** 单计划最多追加的 LLM 补步数量，防止模型失控堆步骤。 */
    private static final int MAX_AUGMENTED_STEPS = 3;

    private final AiModelGatewayService aiModelGatewayService;
    private final AgentToolSchemaRegistry agentToolSchemaRegistry;
    private final ObjectMapper objectMapper;

    /**
     * 一条通过校验的 LLM 附加步骤。
     *
     * @param stepName          步骤名
     * @param stepGoal          步骤目标
     * @param toolName          标准工具名（白名单内）
     * @param toolInput         该步可执行参数（可含 {{step_N.output.xxx}} 占位符）
     * @param completionCriteria 完成条件
     */
    public record AugmentedStep(
            String stepName,
            String stepGoal,
            String toolName,
            Map<String, Object> toolInput,
            String completionCriteria
    ) {
    }

    /**
     * 用 LLM 生成附加步骤。
     *
     * @param userId     用户 ID
     * @param traceId    链路 ID
     * @param intentCode 意图编码（用于 Prompt 上下文）
     * @param userGoal   用户原始目标
     * @return 通过白名单与入参校验的附加步骤；无则空列表
     */
    public List<AugmentedStep> augment(Long userId, String traceId, String intentCode, String userGoal) {
        if (!StringUtils.hasText(userGoal)) {
            return List.of();
        }
        try {
            String output = aiModelGatewayService.chat(
                    AI_SCENE_PLAN_AUGMENT,
                    buildVariables(),
                    buildUserMessage(userGoal.trim()),
                    userId,
                    traceId
            );
            return parseSteps(output);
        } catch (Exception exception) {
            log.debug("LLM 计划补步降级为空，userId={}, traceId={}, error={}", userId, traceId, exception.getMessage());
            return List.of();
        }
    }

    // 只把白名单工具（名 + 入参 key）透给模型，并要求用 {{step_N.output.xxx}} 引用前序输出
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

    private String buildUserMessage(String userGoal) {
        return """
                你是求职 Agent 的计划补步器。用户目标较复杂时，请在模板主干之外补充少量额外步骤。

                规则:
                1. 只输出 JSON 数组，不要解释、Markdown 或多余文本。
                2. toolName 只能是给定白名单工具之一，toolInput 的 key 只能是对应工具的入参。
                3. 需要引用前序步骤输出时，用 {{step_N.output.xxx}} 占位符（N 是步骤序号）。
                4. 不要编造白名单外工具；没有需要补充的步骤就输出 []。

                用户目标:
                """ + "\n" + userGoal;
    }

    // 解析并校验：白名单工具 + 入参 key 合法 + 数量上限；非法步骤丢弃
    private List<AugmentedStep> parseSteps(String output) {
        String json = stripJsonArray(output);
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            List<Map<String, Object>> rows = objectMapper.readValue(json, new TypeReference<>() {
            });
            List<AugmentedStep> steps = new ArrayList<>();
            for (Map<String, Object> row : rows) {
                AugmentedStep step = toStep(row);
                if (step != null) {
                    steps.add(step);
                }
                if (steps.size() >= MAX_AUGMENTED_STEPS) {
                    break;
                }
            }
            return steps;
        } catch (Exception exception) {
            log.debug("LLM 计划补步 JSON 解析失败，output={}, error={}", output, exception.getMessage());
            return List.of();
        }
    }

    // 校验单条：toolName 必须在 Schema 白名单、toolInput key 必须是该工具入参；否则返回 null
    private AugmentedStep toStep(Map<String, Object> row) {
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
        return new AugmentedStep(
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
