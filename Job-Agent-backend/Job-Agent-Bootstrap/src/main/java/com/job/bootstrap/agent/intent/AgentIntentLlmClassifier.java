package com.job.bootstrap.agent.intent;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.job.bootstrap.service.AiModelGatewayService;
import com.job.enums.AiModelTier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 功能: 意图 LLM 兜底分类器（任务5 阶段一）。
 *
 * 设计说明:
 * 1. 规则意图（{@link AgentIntentRouter}）没命中时，才调本组件用大模型兜底分类，即“规则优先 + LLM 兜底”。
 * 2. 复用 AgentMemoryLlmExtractor 的模式：走动态模型网关 INTENT_CLASSIFY 场景，输出受 Schema 约束的 JSON。
 * 3. 不信任模型输出：intentCode 必须在 {@link AgentIntentCode} 白名单内、confidence 达到阈值才采用。
 * 4. 任何异常（场景未配置、模型失败、JSON 非法）都降级为空，保持纯规则行为，绝不卡主流程。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentIntentLlmClassifier {

    private static final String AI_SCENE_INTENT_CLASSIFY = "INTENT_CLASSIFY";
    /** 采用 LLM 意图的最低置信度，低于则保持规则结果（GENERAL_CHAT）。 */
    private static final double MIN_CONFIDENCE = 0.6D;

    private final AiModelGatewayService aiModelGatewayService;
    private final ObjectMapper objectMapper;

    /**
     * 用大模型兜底分类意图。
     *
     * @param userId  用户 ID
     * @param traceId 链路 ID
     * @param message 已脱敏用户输入
     * @return 命中白名单且置信度达标时返回意图；否则空（上层继续用规则结果）
     */
    public Optional<AgentIntentCode> classify(Long userId, String traceId, String message) {
        if (!StringUtils.hasText(message)) {
            return Optional.empty();
        }
        try {
            String output = aiModelGatewayService.chat(
                    AI_SCENE_INTENT_CLASSIFY,
                    AiModelTier.LIGHT, // 意图分类是轻任务，走轻量档（任务 G1）
                    buildVariables(),
                    buildUserMessage(message.trim()),
                    userId,
                    traceId
            );
            return parseIntent(output);
        } catch (Exception exception) {
            log.debug("LLM 意图兜底降级为空，userId={}, traceId={}, error={}", userId, traceId, exception.getMessage());
            return Optional.empty();
        }
    }

    // 传给网关的 Prompt 变量：白名单意图 + 输出 JSON Schema
    private Map<String, Object> buildVariables() {
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("allowedIntents", Arrays.stream(AgentIntentCode.values()).map(Enum::name).toList());
        variables.put("jsonSchema", """
                {
                  "intentCode": "GENERAL_CHAT",
                  "confidence": 0.8
                }
                """);
        return variables;
    }

    // 让模型只做单选分类、输出 JSON，避免解释性文本
    private String buildUserMessage(String message) {
        return """
                你是求职 Agent 的意图分类器。请把用户输入分类到给定意图之一。

                规则:
                1. 只输出 JSON 对象，不要解释、Markdown 或多余文本。
                2. intentCode 只能是允许列表中的某一个。
                3. confidence 取 0~1，表示你对分类的把握；没把握就给低分。

                用户输入:
                """ + "\n" + message;
    }

    // 解析并校验模型输出：白名单 intentCode + 置信度阈值，任一不满足返回空
    private Optional<AgentIntentCode> parseIntent(String output) {
        String json = stripJsonObject(output);
        if (!StringUtils.hasText(json)) {
            return Optional.empty();
        }
        try {
            Map<String, Object> node = objectMapper.readValue(json, new TypeReference<>() {
            });
            AgentIntentCode code = parseCode(node.get("intentCode"));
            if (code == null) {
                return Optional.empty();
            }
            double confidence = toDouble(node.get("confidence"));
            return confidence >= MIN_CONFIDENCE ? Optional.of(code) : Optional.empty();
        } catch (Exception exception) {
            log.debug("LLM 意图 JSON 解析失败，output={}, error={}", output, exception.getMessage());
            return Optional.empty();
        }
    }

    private AgentIntentCode parseCode(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return AgentIntentCode.valueOf(String.valueOf(value).trim());
        } catch (Exception exception) {
            return null;
        }
    }

    private double toDouble(Object value) {
        if (value == null) {
            return 0D;
        }
        try {
            return Double.parseDouble(String.valueOf(value).trim());
        } catch (Exception exception) {
            return 0D;
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
