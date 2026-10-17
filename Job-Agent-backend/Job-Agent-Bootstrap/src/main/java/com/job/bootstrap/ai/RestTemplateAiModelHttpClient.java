package com.job.bootstrap.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.job.common.entity.ai.AiModelConfig;
import com.job.common.entity.base.ResultCodeEnum;
import com.job.exception.BizException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URL;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
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
 * 3. 流式调用（任务 G5）不走 RestTemplate（阻塞式不支持增量读取），
 *    用 JDK 原生 HttpURLConnection 逐行读 SSE——项目不引入 webflux/okhttp，零新依赖。
 */
@Slf4j
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

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(model.getApiKey());

        RestTemplate restTemplate = buildRestTemplate(model);
        ResponseEntity<String> response = restTemplate.exchange(
                buildChatUrl(model),
                HttpMethod.POST,
                new HttpEntity<>(buildRequestBody(model, systemPrompt, userMessage), headers),
                String.class
        );

        return parseModelResponse(response.getBody(), systemPrompt, userMessage);
    }

    @Override
    public void chatStream(AiModelConfig model, String systemPrompt, String userMessage, TokenConsumer consumer) {
        if (!StringUtils.hasText(model.getApiKey())) {
            throw new BizException("模型 API Key 为空：" + model.getModelCode());
        }

        Map<String, Object> body = buildRequestBody(model, systemPrompt, userMessage);
        body.put("stream", true);

        int timeoutMs = (int) Duration.ofSeconds(
                model.getTimeoutSeconds() == null ? 45 : model.getTimeoutSeconds()).toMillis();
        HttpURLConnection connection = null;
        try {
            URL url = URI.create(buildChatUrl(model)).toURL();
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(timeoutMs);
            connection.setReadTimeout(timeoutMs);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Authorization", "Bearer " + model.getApiKey().trim());
            connection.setDoOutput(true);
            connection.getOutputStream().write(objectMapper.writeValueAsBytes(body));
            connection.getOutputStream().flush();

            int status = connection.getResponseCode();
            InputStream stream = status >= 200 && status < 300
                    ? connection.getInputStream()
                    : connection.getErrorStream();
            if (stream == null) {
                throw new BizException("模型流式响应为空，HTTP " + status);
            }
            readSseStream(stream, consumer);
        } catch (BizException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BizException(
                    ResultCodeEnum.SYSTEM_ERROR.getCode(),
                    "模型流式调用失败：" + exception.getMessage(),
                    exception
            );
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    // 逐行读 SSE 流：喂给解析器拿完整事件，再取 delta.content 回调给消费者
    private void readSseStream(InputStream stream, TokenConsumer consumer) throws Exception {
        SseStreamParser parser = new SseStreamParser();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                for (String event : parser.feed(line + "\n")) {
                    emitDelta(event, consumer);
                }
            }
            for (String event : parser.flush()) {
                emitDelta(event, consumer);
            }
        }
    }

    // 解析单个 SSE 事件的 delta 内容；[DONE]/空/非内容帧（如 usage）安全跳过
    private void emitDelta(String event, TokenConsumer consumer) {
        if (!StringUtils.hasText(event) || "[DONE]".equals(event)) {
            return;
        }
        try {
            JsonNode root = objectMapper.readTree(event);
            String delta = root.path("choices").path(0).path("delta").path("content").asText(null);
            if (StringUtils.hasText(delta)) {
                consumer.onToken(delta);
            }
        } catch (Exception exception) {
            // 单条事件解析失败不中断流式（如仅含 usage 的尾帧）
            log.debug("SSE 事件解析跳过，event={}, error={}", event, exception.getMessage());
        }
    }

    // 阻塞与流式共用的请求体（流式由调用方追加 stream=true）
    private Map<String, Object> buildRequestBody(AiModelConfig model, String systemPrompt, String userMessage) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model.getModelIdentifier());
        body.put("temperature", model.getTemperature());
        body.put("max_tokens", model.getMaxTokens());
        body.put("messages", List.of(
                Map.of("role", "system", "content", safeText(systemPrompt)),
                Map.of("role", "user", "content", safeText(userMessage))
        ));
        return body;
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
