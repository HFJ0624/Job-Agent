package com.job.bootstrap.rag.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.job.bootstrap.rag.config.RagProperties;
import com.job.bootstrap.rag.service.RagEmbeddingService;
import com.job.common.entity.base.ResultCodeEnum;
import com.job.exception.BizException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 作者:hfj
 * 功能:RAG Embedding 服务实现
 * 日期:2026/6/14
 */
@Service
public class RagEmbeddingServiceImpl implements RagEmbeddingService {

    private static final String REQUEST_MODE_AUTO = "auto";
    private static final String REQUEST_MODE_OPENAI_TEXT = "openai-text";
    private static final String REQUEST_MODE_ARK_MULTIMODAL = "ark-multimodal";

    private final RagProperties ragProperties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public RagEmbeddingServiceImpl(RagProperties ragProperties, ObjectMapper objectMapper) {
        this.ragProperties = ragProperties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(resolveTimeoutSeconds(ragProperties.getEmbedding())))
                .build();
    }

    /**
     * 将文本转换成 embedding 向量。
     *
     * @param text 待向量化文本
     * @return embedding 向量
     */
    @Override
    public float[] embed(String text) {
        if (!StringUtils.hasText(text)) {
            throw new IllegalArgumentException("RAG 向量化文本不能为空");
        }

        /*
         * 1. 当前 RAG 的知识来源是数据库文本: 简历、JD、公司和沟通记录。
         * 2. 即使使用 doubao-embedding-vision-* 多模态模型，这里第一阶段也只传 text 内容。
         * 3. 后续如果要把简历截图、作品集图片一起入库，可以在这里扩展 image_url / image_base64 输入。
         */
        RagProperties.Embedding embedding = ragProperties.getEmbedding();
        validateConfig(embedding);

        try {
            String requestBody = objectMapper.writeValueAsString(buildRequestBody(embedding, text.trim()));
            HttpRequest request = HttpRequest.newBuilder(URI.create(resolveUrl(embedding)))
                    .timeout(Duration.ofSeconds(resolveTimeoutSeconds(embedding)))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + embedding.getApiKey())
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new BizException(
                        ResultCodeEnum.BUSINESS_ERROR.getCode(),
                        buildHttpErrorMessage(embedding, response)
                );
            }

            float[] vector = parseVector(response.body());
            validateVector(vector);
            return vector;
        } catch (BizException exception) {
            throw exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new BizException(ResultCodeEnum.BUSINESS_ERROR.getCode(), "RAG Embedding 请求被中断，请稍后重试。", exception);
        } catch (IOException exception) {
            throw new BizException(ResultCodeEnum.BUSINESS_ERROR.getCode(), "RAG Embedding 请求或响应解析失败，请检查火山方舟网络和接口返回。", exception);
        } catch (RuntimeException exception) {
            throw new BizException(ResultCodeEnum.BUSINESS_ERROR.getCode(), "RAG Embedding 调用失败，请检查 embedding 配置。", exception);
        }
    }

    private Map<String, Object> buildRequestBody(RagProperties.Embedding embedding, String text) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", embedding.getModelName());
        body.put("input", buildInput(embedding, text));

        /*
         * 1. pgvector 需要能直接写入的浮点数组。
         * 2. encoding_format=float 可以避免供应商返回 base64 后还要二次解码。
         * 3. 如果火山方舟后续对多模态接口不需要该字段，可以在 yml 中置空。
         */
        if (StringUtils.hasText(embedding.getEncodingFormat())) {
            body.put("encoding_format", embedding.getEncodingFormat());
        }
        return body;
    }

    private Object buildInput(RagProperties.Embedding embedding, String text) {
        if (isArkMultimodalMode(embedding)) {
            /*
             * 火山方舟 doubao-embedding-vision-* 是多模态向量模型。
             * 多模态接口的输入需要声明内容类型；当前只把数据库文本作为 text part 送入。
             */
            return List.of(Map.of(
                    "type", "text",
                    "text", text
            ));
        }

        /*
         * 普通文本 embedding 模型继续使用 OpenAI 兼容输入。
         */
        return text;
    }

    private boolean isArkMultimodalMode(RagProperties.Embedding embedding) {
        String requestMode = normalizeMode(embedding.getRequestMode());
        if (REQUEST_MODE_ARK_MULTIMODAL.equals(requestMode)) {
            return true;
        }
        if (REQUEST_MODE_OPENAI_TEXT.equals(requestMode)) {
            return false;
        }

        String modelName = embedding.getModelName().toLowerCase(Locale.ROOT);
        return modelName.contains("vision") || modelName.contains("multimodal");
    }

    private String resolveUrl(RagProperties.Embedding embedding) {
        return trimEndSlash(embedding.getBaseUrl()) + normalizePath(resolveApiPath(embedding));
    }

    private String resolveApiPath(RagProperties.Embedding embedding) {
        if (StringUtils.hasText(embedding.getApiPath())) {
            return embedding.getApiPath();
        }
        if (isArkMultimodalMode(embedding)) {
            return embedding.getMultimodalApiPath();
        }
        return embedding.getTextApiPath();
    }

    private float[] parseVector(String responseBody) throws IOException {
        JsonNode root = objectMapper.readTree(responseBody);
        JsonNode embeddingNode = findEmbeddingNode(root);
        if (embeddingNode == null || !embeddingNode.isArray() || embeddingNode.isEmpty()) {
            throw new BizException("Embedding 接口没有返回可用的 embedding 数组");
        }

        float[] vector = new float[embeddingNode.size()];
        for (int i = 0; i < embeddingNode.size(); i++) {
            double value = embeddingNode.get(i).asDouble(Double.NaN);
            if (!Double.isFinite(value)) {
                throw new BizException("Embedding 接口返回了非法向量值，位置=" + i);
            }
            vector[i] = (float) value;
        }
        return vector;
    }

    private JsonNode findEmbeddingNode(JsonNode root) {
        /*
         * 兼容几类常见响应:
         * 1. OpenAI 风格: { "data": [ { "embedding": [...] } ] }
         * 2. 单对象风格: { "data": { "embedding": [...] } }
         * 3. 扁平风格: { "embedding": [...] }
         * 4. 包装风格: { "result": { "embedding": [...] } }
         */
        JsonNode data = root.path("data");
        if (data.isArray() && !data.isEmpty()) {
            JsonNode embedding = data.get(0).path("embedding");
            if (embedding.isArray()) {
                return embedding;
            }
        }
        if (data.path("embedding").isArray()) {
            return data.path("embedding");
        }
        if (root.path("embedding").isArray()) {
            return root.path("embedding");
        }
        if (root.path("result").path("embedding").isArray()) {
            return root.path("result").path("embedding");
        }
        return null;
    }

    private void validateVector(float[] vector) {
        Integer expectedDimension = ragProperties.getPgvector().getDimension();
        if (vector == null || vector.length == 0) {
            throw new BizException("Embedding 模型返回了空向量");
        }
        if (expectedDimension != null && vector.length != expectedDimension) {
            throw new BizException(
                    "Embedding 向量维度不匹配，配置维度=" + expectedDimension + "，实际维度=" + vector.length +
                            "。请把 job.rag.pgvector.dimension 改成模型真实维度，并重建 job_knowledge 表。"
            );
        }

        for (float value : vector) {
            if (!Float.isFinite(value)) {
                throw new BizException("Embedding 向量中存在非法数值");
            }
        }
    }

    private void validateConfig(RagProperties.Embedding embedding) {
        if (!StringUtils.hasText(embedding.getApiKey())) {
            throw new BizException("请配置 job.rag.embedding.api-key");
        }
        if (!StringUtils.hasText(embedding.getBaseUrl())) {
            throw new BizException("请配置 job.rag.embedding.base-url");
        }
        if (!StringUtils.hasText(embedding.getModelName())) {
            throw new BizException("请配置 job.rag.embedding.model-name");
        }
    }

    private String buildHttpErrorMessage(RagProperties.Embedding embedding, HttpResponse<String> response) {
        String providerMessage = extractProviderErrorMessage(response.body());
        StringBuilder message = new StringBuilder();
        message.append("RAG Embedding 调用火山方舟失败，HTTP状态码=")
                .append(response.statusCode())
                .append("，模型=")
                .append(embedding.getModelName())
                .append("，接口路径=")
                .append(resolveApiPath(embedding))
                .append("。");
        if (StringUtils.hasText(providerMessage)) {
            message.append("火山方舟返回：").append(providerMessage);
        }
        message.append("如果控制台 API接入 页面给出的路径不同，请配置 job.rag.embedding.api-path。");
        return message.toString();
    }

    private String extractProviderErrorMessage(String responseBody) {
        if (!StringUtils.hasText(responseBody)) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode error = root.path("error");
            if (StringUtils.hasText(error.path("message").asText())) {
                return error.path("message").asText();
            }
            if (StringUtils.hasText(root.path("message").asText())) {
                return root.path("message").asText();
            }
        } catch (Exception ignored) {
            return responseBody;
        }
        return responseBody;
    }

    private String normalizeMode(String requestMode) {
        if (!StringUtils.hasText(requestMode)) {
            return REQUEST_MODE_AUTO;
        }
        return requestMode.trim().toLowerCase(Locale.ROOT);
    }

    private long resolveTimeoutSeconds(RagProperties.Embedding embedding) {
        Long timeoutSeconds = embedding.getTimeoutSeconds();
        if (timeoutSeconds == null || timeoutSeconds <= 0) {
            return 60L;
        }
        return timeoutSeconds;
    }

    private String trimEndSlash(String value) {
        String result = value.trim();
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    private String normalizePath(String path) {
        if (!StringUtils.hasText(path)) {
            return "";
        }
        String result = path.trim();
        return result.startsWith("/") ? result : "/" + result;
    }
}
