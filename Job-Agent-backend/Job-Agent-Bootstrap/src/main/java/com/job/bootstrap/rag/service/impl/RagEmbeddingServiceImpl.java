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

        //1.输入参数验证：空文本无法生成有意义的向量
        if (!StringUtils.hasText(text)) {
            throw new IllegalArgumentException("RAG 向量化文本不能为空");
        }

        /*
         * 📌 当前RAG知识来源说明：
         * 1. 第一阶段仅处理数据库中的纯文本内容：简历、JD、公司信息和沟通记录
         * 2. 即使使用doubao-embedding-vision-*多模态模型，也只传递text部分
         * 3. 未来扩展点：支持简历截图、作品集图片等多模态内容入库
         *    届时可在此处扩展image_url或image_base64参数
         */
        RagProperties.Embedding embedding = ragProperties.getEmbedding();
        validateConfig(embedding);

        try {
            // 构建请求体并序列化为JSON
            String requestBody = objectMapper.writeValueAsString(buildRequestBody(embedding, text.trim()));

            // 构建HTTP请求
            HttpRequest request = HttpRequest.newBuilder(URI.create(resolveUrl(embedding)))
                    .timeout(Duration.ofSeconds(resolveTimeoutSeconds(embedding)))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + embedding.getApiKey())
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                    .build();

            // 发送HTTP请求并获取响应
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new BizException(
                        ResultCodeEnum.BUSINESS_ERROR.getCode(),
                        buildHttpErrorMessage(embedding, response)
                );
            }

            // 解析响应体获取向量
            float[] vector = parseVector(response.body());

            // 验证向量的合法性和维度正确性
            validateVector(vector);

            return vector;
        } catch (BizException exception) {
            throw exception;
        } catch (InterruptedException exception) {
            // 线程中断异常：恢复中断状态并抛出业务异常
            Thread.currentThread().interrupt();
            throw new BizException(ResultCodeEnum.BUSINESS_ERROR.getCode(), "RAG Embedding 请求被中断，请稍后重试。", exception);
        } catch (IOException exception) {
            // IO异常：网络问题或JSON解析失败
            throw new BizException(ResultCodeEnum.BUSINESS_ERROR.getCode(), "RAG Embedding 请求或响应解析失败，请检查火山方舟网络和接口返回。", exception);
        } catch (RuntimeException exception) {
            // 其他运行时异常：统一包装为业务异常
            throw new BizException(ResultCodeEnum.BUSINESS_ERROR.getCode(), "RAG Embedding 调用失败，请检查 embedding 配置。", exception);
        }
    }

    /***
     * 构建Embedding API的请求体
     *
     * @param embedding embedding配置
     * @param text 待向量化的文本
     * @return 构建好的请求体Map
     */
    private Map<String, Object> buildRequestBody(RagProperties.Embedding embedding, String text) {

        // 使用LinkedHashMap保持字段顺序，便于调试
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", embedding.getModelName());
        body.put("input", buildInput(embedding, text));

        /*
         * ⚡ 性能优化关键：encoding_format=float
         * 1. pgvector数据库需要直接写入浮点数组
         * 2. 默认情况下部分API会返回base64编码的向量，需要二次解码
         * 3. 设置encoding_format=float可以让API直接返回浮点数组
         * 4. 如果某些API不需要该字段，可以在yml中置空
         */
        if (StringUtils.hasText(embedding.getEncodingFormat())) {
            body.put("encoding_format", embedding.getEncodingFormat());
        }

        return body;
    }

    /***
     * 构建API请求的input部分
     *
     * @param embedding embedding配置
     * @param text 待向量化的文本
     * @return 对应格式的input对象
     */
    private Object buildInput(RagProperties.Embedding embedding, String text) {
        if (isArkMultimodalMode(embedding)) {

            /*
             * 火山方舟doubao-embedding-vision-*多模态向量模型要求：
             * 输入必须是数组形式，每个元素包含type和对应的内容
             * 当前只支持文本类型，未来可扩展image类型
             */
            return List.of(Map.of(
                    "type", "text",
                    "text", text
            ));
        }

        /*
         * OpenAI兼容的普通文本embedding模型：
         * 直接使用字符串作为input即可
         */
        return text;
    }

    /***
     * 判断是否使用火山方舟多模态模式
     *
     * @param embedding embedding配置
     * @return true表示使用多模态模式，false表示使用普通文本模式
     */
    private boolean isArkMultimodalMode(RagProperties.Embedding embedding) {
        String requestMode = normalizeMode(embedding.getRequestMode());

        // 显式指定多模态模式
        if (REQUEST_MODE_ARK_MULTIMODAL.equals(requestMode)) {
            return true;
        }

        // 显式指定OpenAI文本模式
        if (REQUEST_MODE_OPENAI_TEXT.equals(requestMode)) {
            return false;
        }

        // 自动检测模式：根据模型名称判断
        String modelName = embedding.getModelName().toLowerCase(Locale.ROOT);
        return modelName.contains("vision") || modelName.contains("multimodal");
    }

    private String resolveUrl(RagProperties.Embedding embedding) {
        //解析并构建完整的API请求URL
        return trimEndSlash(embedding.getBaseUrl()) + normalizePath(resolveApiPath(embedding));
    }

    //解析API请求路径
    private String resolveApiPath(RagProperties.Embedding embedding) {

        // 显式配置的路径优先级最高
        if (StringUtils.hasText(embedding.getApiPath())) {
            return embedding.getApiPath();
        }

        // 多模态模型使用多模态专用路径
        if (isArkMultimodalMode(embedding)) {
            return embedding.getMultimodalApiPath();
        }

        // 普通文本模型使用文本专用路径
        return embedding.getTextApiPath();
    }

    /***
     * 解析API响应体获取embedding向量
     *
     * @param responseBody API响应的JSON字符串
     * @return float数组格式的embedding向量
     * @throws IOException JSON解析失败时抛出
     */
    private float[] parseVector(String responseBody) throws IOException {
        JsonNode root = objectMapper.readTree(responseBody);
        JsonNode embeddingNode = findEmbeddingNode(root);

        // 检查是否找到有效的embedding数组
        if (embeddingNode == null || !embeddingNode.isArray() || embeddingNode.isEmpty()) {
            throw new BizException("Embedding 接口没有返回可用的 embedding 数组");
        }

        // 将JSON数组转换为float数组
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
        // 1. OpenAI标准风格：data数组中的第一个元素的embedding
        JsonNode data = root.path("data");
        if (data.isArray() && !data.isEmpty()) {
            JsonNode embedding = data.get(0).path("embedding");
            if (embedding.isArray()) {
                return embedding;
            }
        }

        // 2. 单对象风格：data对象的embedding字段
        if (data.path("embedding").isArray()) {
            return data.path("embedding");
        }

        // 3. 扁平风格：根节点直接有embedding字段
        if (root.path("embedding").isArray()) {
            return root.path("embedding");
        }

        // 4. 包装风格：result对象的embedding字段
        if (root.path("result").path("embedding").isArray()) {
            return root.path("result").path("embedding");
        }

        // 所有格式都不匹配
        return null;
    }

    //验证生成的embedding向量的合法性
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

    //验证embedding配置的完整性
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

    //构建HTTP错误响应的详细错误信息
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

    //从API响应中提取服务提供商的错误信息
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

    //标准化请求模式字符串
    private String normalizeMode(String requestMode) {
        if (!StringUtils.hasText(requestMode)) {
            return REQUEST_MODE_AUTO;
        }
        return requestMode.trim().toLowerCase(Locale.ROOT);
    }

    //解析请求超时时间
    private long resolveTimeoutSeconds(RagProperties.Embedding embedding) {
        Long timeoutSeconds = embedding.getTimeoutSeconds();
        if (timeoutSeconds == null || timeoutSeconds <= 0) {
            return 60L;
        }
        return timeoutSeconds;
    }

    //去除字符串末尾的所有斜杠
    private String trimEndSlash(String value) {
        String result = value.trim();
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    //标准化路径字符串
    private String normalizePath(String path) {
        if (!StringUtils.hasText(path)) {
            return "";
        }
        String result = path.trim();
        return result.startsWith("/") ? result : "/" + result;
    }
}
