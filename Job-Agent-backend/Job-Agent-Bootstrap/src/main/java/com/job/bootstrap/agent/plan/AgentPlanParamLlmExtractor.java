package com.job.bootstrap.agent.plan;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.job.bootstrap.service.AiModelGatewayService;
import com.job.enums.AiModelTier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 功能: 计划参数 LLM 兜底抽取器（任务5 阶段二）。
 *
 * 设计说明:
 * 1. 正则抽取（{@link AgentPlanParameterExtractor}）后必填参数仍缺失时，才用本组件调 LLM 补抽。
 * 2. 只抽取调用方指定的白名单 key，且按既定类型校验（ID→Long、minSalary→Integer、名称/城市/关键词→String），
 *    白名单外或类型不符的一律丢弃，不信任模型原始输出。
 * 3. 与正则结果合并由上层用 putIfAbsent 保证“正则优先、LLM 只补空缺”。
 * 4. 任何异常都降级为空 Map，不影响计划生成主流程。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentPlanParamLlmExtractor {

    private static final String AI_SCENE_PARAM_EXTRACT = "PARAM_EXTRACT";
    /** 需要按 Long 校验的 ID 类参数。 */
    private static final Set<String> LONG_KEYS = Set.of("resumeId", "jobId", "applicationId", "mockSessionId");
    /** 需要按 Integer 校验的参数。 */
    private static final Set<String> INT_KEYS = Set.of("minSalary");
    /** 字符串类参数（名称/城市/关键词等）。 */
    private static final Set<String> STRING_KEYS = Set.of("resumeName", "jobTitle", "city", "keyword");

    private final AiModelGatewayService aiModelGatewayService;
    private final ObjectMapper objectMapper;

    /**
     * 用 LLM 补抽缺失的计划参数。
     *
     * @param userId      用户 ID
     * @param traceId     链路 ID
     * @param message     用户原始输入（已脱敏）
     * @param allowedKeys 本轮允许抽取的白名单 key（缺失必填参数及其自然语言替代）
     * @return 仅含白名单 key 且类型合法的参数；无则空 Map
     */
    public Map<String, Object> extract(Long userId, String traceId, String message, Set<String> allowedKeys) {
        if (!StringUtils.hasText(message) || allowedKeys == null || allowedKeys.isEmpty()) {
            return Map.of();
        }
        try {
            String output = aiModelGatewayService.chat(
                    AI_SCENE_PARAM_EXTRACT,
                    AiModelTier.LIGHT, // 参数抽取是轻任务，走轻量档（任务 G1）
                    buildVariables(allowedKeys),
                    buildUserMessage(message.trim()),
                    userId,
                    traceId
            );
            return parseParams(output, allowedKeys);
        } catch (Exception exception) {
            log.debug("LLM 参数兜底抽取降级为空，userId={}, traceId={}, error={}", userId, traceId, exception.getMessage());
            return Map.of();
        }
    }

    // 传给网关的 Prompt 变量：白名单 key + 输出 JSON Schema 示例
    private Map<String, Object> buildVariables(Set<String> allowedKeys) {
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("allowedKeys", allowedKeys.stream().sorted().toList());
        variables.put("jsonSchema", """
                {
                  "resumeId": 1,
                  "jobId": 2,
                  "resumeName": "黄锋杰(后端)简历",
                  "jobTitle": "Java 后端开发",
                  "city": "杭州",
                  "keyword": "Java",
                  "minSalary": 15000
                }
                """);
        return variables;
    }

    // 让模型只按白名单 key 抽参数、输出 JSON，不编造
    private String buildUserMessage(String message) {
        return """
                你是求职 Agent 的参数抽取器。请从用户输入中抽取给定白名单 key 对应的参数。

                规则:
                1. 只输出 JSON 对象，不要解释、Markdown 或多余文本。
                2. 只能输出白名单里的 key，白名单外的一律不要输出。
                3. ID 类输出数字，minSalary 输出数字（元），名称/城市/关键词输出字符串。
                4. 抽不到的就不要放该 key，不要编造。

                用户输入:
                """ + "\n" + message;
    }

    // 解析并校验：仅保留白名单 key，且按类型转换，非法丢弃
    private Map<String, Object> parseParams(String output, Set<String> allowedKeys) {
        String json = stripJsonObject(output);
        if (!StringUtils.hasText(json)) {
            return Map.of();
        }
        try {
            Map<String, Object> node = objectMapper.readValue(json, new TypeReference<>() {
            });
            Map<String, Object> result = new LinkedHashMap<>();
            for (String key : allowedKeys) {
                Object value = coerce(key, node.get(key));
                if (value != null) {
                    result.put(key, value);
                }
            }
            return result;
        } catch (Exception exception) {
            log.debug("LLM 参数 JSON 解析失败，output={}, error={}", output, exception.getMessage());
            return Map.of();
        }
    }

    // 按 key 的既定类型转换，非法/未定义类型返回 null（丢弃）
    private Object coerce(String key, Object raw) {
        if (raw == null) {
            return null;
        }
        try {
            if (LONG_KEYS.contains(key)) {
                return Long.valueOf(String.valueOf(raw).trim());
            }
            if (INT_KEYS.contains(key)) {
                return Integer.valueOf(String.valueOf(raw).trim());
            }
            if (STRING_KEYS.contains(key)) {
                String text = String.valueOf(raw).trim();
                return text.isEmpty() ? null : text;
            }
            return null;
        } catch (Exception exception) {
            return null;
        }
    }

    // 去掉 ```json 围栏，截取第一个 { 到最后一个 } 之间的 JSON 对象
    private String stripJsonObject(String output) {
        if (!StringUtils.hasText(output)) {
            return "";
        }
        String text = output.trim();
        if (text.startsWith("```")) {
            text = text.replaceFirst("(?s)^```(?:json)?\\s*", "");
            text = text.replaceFirst("(?s)\\s*```$", "");
        }
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        return text;
    }
}
