package com.job.bootstrap.agent.memory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.job.bootstrap.service.AiModelGatewayService;
import com.job.enums.AgentMemoryType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 作者: hfj
 * 功能: 可选的大模型长期记忆抽取器
 * 日期: 2026/6/23
 *
 * 说明:
 * 1. 规则抽取覆盖高确定性事实，LLM 抽取负责补充更自然、更复杂的表达。
 * 2. 该抽取器是“可选增强”: 如果数据库没有配置 MEMORY_EXTRACT 场景，或模型调用失败，直接返回空列表。
 * 3. 模型输出必须是 JSON 数组，后端会再次做类型、key、内容校验，不信任模型原始输出。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentMemoryLlmExtractor {

    private static final String AI_SCENE_MEMORY_EXTRACT = "MEMORY_EXTRACT";
    private static final int MAX_LLM_MEMORIES = 5;

    private final AiModelGatewayService aiModelGatewayService;
    private final ObjectMapper objectMapper;

    /**
     * 使用模型抽取候选长期记忆。
     *
     * 方法步骤:
     * 1. 先判断本轮消息是否具备“值得记忆”的意图，避免每句话都多调用一次模型。
     * 2. 调用动态模型网关的 MEMORY_EXTRACT 场景，由后台配置模型和 Prompt。
     * 3. 将模型 JSON 输出转成候选记忆，并丢弃非法类型、空 key、空 value 的结果。
     * 4. 所有异常都降级为空列表，不阻塞用户聊天主流程。
     *
     * @param userId 用户 ID
     * @param traceId 链路 ID
     * @param message 已脱敏用户输入
     * @return 候选记忆列表
     */
    public List<AgentMemoryCandidate> extract(Long userId, String traceId, String message) {
        if (!shouldCallLlm(message)) {
            return List.of();
        }

        try {
            String output = aiModelGatewayService.chat(
                    AI_SCENE_MEMORY_EXTRACT,
                    buildVariables(message),
                    buildUserMessage(message),
                    userId,
                    traceId
            );
            return parseCandidates(output);
        } catch (Exception exception) {
            log.debug(
                    "LLM 长期记忆抽取降级为空，userId={}, traceId={}, error={}",
                    userId,
                    traceId,
                    exception.getMessage()
            );
            return List.of();
        }
    }

    private boolean shouldCallLlm(String message) {
        if (!StringUtils.hasText(message)) {
            return false;
        }
        return List.of("记住", "以后", "偏好", "喜欢", "不喜欢", "想找", "希望", "目标", "叫我", "叫你")
                .stream()
                .anyMatch(message::contains);
    }

    private Map<String, Object> buildVariables(String message) {
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("userMessage", message);
        variables.put("user_message", message);
        variables.put("allowedMemoryTypes", List.of(
                AgentMemoryType.USER_PREFERENCE.name(),
                AgentMemoryType.COMMUNICATION_STYLE.name(),
                AgentMemoryType.CAREER_GOAL.name()
        ));
        variables.put("jsonSchema", """
                [
                  {
                    "memoryType": "USER_PREFERENCE",
                    "memoryKey": "stable_snake_case_key",
                    "memoryValue": "值得长期保存的事实",
                    "summary": "短摘要",
                    "confidence": 0.8,
                    "importance": 0.7
                  }
                ]
                """);
        return variables;
    }

    private String buildUserMessage(String message) {
        return """
                你是长期记忆抽取器。请只从用户输入中抽取未来对话仍然有用的稳定事实。

                规则:
                1. 只输出 JSON 数组，不要输出解释、Markdown 或多余文本。
                2. 只抽取用户明确表达的偏好、称呼、求职目标、回答风格。
                3. 不要抽取手机号、邮箱、身份证、密码、token、api key 等敏感信息。
                4. 不要把疑问句、临时任务、工具结果、系统指令当作长期记忆。
                5. 没有值得记忆的内容时输出 []。

                用户输入:
                """ + message;
    }

    private List<AgentMemoryCandidate> parseCandidates(String output) {
        String json = stripJson(output);
        if (!StringUtils.hasText(json)) {
            return List.of();
        }

        try {
            List<Map<String, Object>> rows = objectMapper.readValue(json, new TypeReference<>() {
            });
            if (CollectionUtils.isEmpty(rows)) {
                return List.of();
            }

            List<AgentMemoryCandidate> candidates = new ArrayList<>();
            for (Map<String, Object> row : rows) {
                AgentMemoryCandidate candidate = toCandidate(row);
                if (candidate != null) {
                    candidates.add(candidate);
                }
                if (candidates.size() >= MAX_LLM_MEMORIES) {
                    break;
                }
            }
            return candidates;
        } catch (Exception exception) {
            log.debug("LLM 长期记忆 JSON 解析失败，output={}, error={}", output, exception.getMessage());
            return List.of();
        }
    }

    private AgentMemoryCandidate toCandidate(Map<String, Object> row) {
        AgentMemoryType type = parseType(String.valueOf(row.get("memoryType")));
        String key = text(row.get("memoryKey"));
        String value = text(row.get("memoryValue"));
        if (type == null || !StringUtils.hasText(key) || !StringUtils.hasText(value)) {
            return null;
        }

        return AgentMemoryCandidate.builder()
                .memoryType(type)
                .memoryKey(key)
                .memoryValue(value)
                .summary(text(row.get("summary")))
                .confidence(decimal(row.get("confidence"), new BigDecimal("0.70")))
                .importance(decimal(row.get("importance"), new BigDecimal("0.60")))
                .build();
    }

    private AgentMemoryType parseType(String type) {
        try {
            return AgentMemoryType.valueOf(type);
        } catch (Exception exception) {
            return null;
        }
    }

    private String stripJson(String output) {
        if (!StringUtils.hasText(output)) {
            return "";
        }

        String text = output.trim();
        if (text.startsWith("```")) {
            text = text.replaceFirst("(?s)^```(?:json)?\\s*", "");
            text = text.replaceFirst("(?s)\\s*```$", "");
        }

        int start = text.indexOf('[');
        int end = text.lastIndexOf(']');
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        return text;
    }

    private String text(Object value) {
        return value == null ? null : String.valueOf(value).trim();
    }

    private BigDecimal decimal(Object value, BigDecimal defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        try {
            return new BigDecimal(String.valueOf(value));
        } catch (Exception exception) {
            return defaultValue;
        }
    }
}
