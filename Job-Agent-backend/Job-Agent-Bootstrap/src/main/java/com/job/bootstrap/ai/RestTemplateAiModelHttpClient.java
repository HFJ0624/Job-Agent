package com.job.bootstrap.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.job.common.entity.ai.AiModelConfig;
import com.job.common.entity.base.ResultCodeEnum;
import com.job.exception.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 功能: 基于 RestTemplate 的 OpenAI 兼容模型 HTTP 客户端（任务8）。
 *
 * 说明:
 * 1. 从 AiModelGatewayServiceImpl 抽出的“HTTP 调用 + 响应解析”逻辑，行为与原实现一致。
 * 2. 抽出后可被 mock，使网关的重试/熔断/限流单测不依赖真实供应商。
 */
@Component
@RequiredArgsConstructor
public class RestTemplateAiModelHttpClient implements AiModelHttpClient {

    private static final String DEFAULT_CHAT_PATH = "/chat/completions";

    private final ObjectMapper objectMapper;

    @Override
    public AiModelCallResponse chat(AiModelConfig model, String systemPrompt, String userMessage) {
        if (!StringUtils.hasText(model.getApiKey())) {
            throw new BizException("模型 API Key 为空：" + model.getModelCode());
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model.getModelIdentifier());
        body.put("temperature", model.getTemperature());
        body.put("max_tokens", model.getMaxTokens());
        body.put("messages", List.of(
                Map.of("role", "system", "content", safeText(systemPrompt)),
                Map.of("role", "user", "content", safeText(userMessage))
        ));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(model.getApiKey());

        RestTemplate restTemplate = buildRestTemplate(model);
        ResponseEntity<String> response = restTemplate.exchange(
                buildChatUrl(model),
                HttpMethod.POST,
                new HttpEntity<>(body, headers),
                String.class
        );

        return parseModelResponse(response.getBody(), systemPrompt, userMessage);
    }

    private AiModelCallResponse parseModelResponse(String responseBody, String systemPrompt, String userMessage) {
        if (!StringUtils.hasText(responseBody)) {
            throw new BizException("模型响应为空");
        }
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            String content = root.path("choices").path(0).path("message").path("content").asText();
            if (!StringUtils.hasText(content)) {
                throw new BizException("模型响应没有 content");
            }
            int inputTokens = root.path("usage").path("prompt_tokens").asInt(estimateTokens(systemPrompt + "\n" + userMessage));
            int outputTokens = root.path("usage").path("completion_tokens").asInt(estimateTokens(content));
            return new AiModelCallResponse(content, inputTokens, outputTokens);
        } catch (BizException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BizException(
                    ResultCodeEnum.SYSTEM_ERROR.getCode(),
                    "模型响应解析失败：" + exception.getMessage(),
                    exception
            );
        }
    }

    private RestTemplate buildRestTemplate(AiModelConfig model) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        Duration timeout = Duration.ofSeconds(model.getTimeoutSeconds() == null ? 45 : model.getTimeoutSeconds());
        factory.setConnectTimeout(timeout);
        factory.setReadTimeout(timeout);
        return new RestTemplate(factory);
    }

    private String buildChatUrl(AiModelConfig model) {
        String baseUrl = model.getBaseUrl().trim();
        String chatPath = StringUtils.hasText(model.getChatPath()) ? model.getChatPath().trim() : DEFAULT_CHAT_PATH;
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        if (!chatPath.startsWith("/")) {
            chatPath = "/" + chatPath;
        }
        return baseUrl + chatPath;
    }

    private int estimateTokens(String text) {
        if (!StringUtils.hasText(text)) {
            return 0;
        }
        return Math.max(1, (int) Math.ceil(text.length() / 4.0));
    }

    private String safeText(String value) {
        return value == null ? "" : value;
    }
}
