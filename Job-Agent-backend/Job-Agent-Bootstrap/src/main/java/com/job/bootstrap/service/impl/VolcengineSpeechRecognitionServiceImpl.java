package com.job.bootstrap.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.job.bootstrap.config.VolcengineAsrProperties;
import com.job.bootstrap.service.SpeechRecognitionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 功能: 火山引擎/豆包语音 ASR 语音识别实现。
 *
 * 说明:
 * 1. 优先支持新版豆包语音 API Key，匹配新版控制台“API Key 接入”。
 * 2. 保留旧版 submit 接口，兼容之前 appId/token/cluster 的配置。
 * 3. 新版接口的字段名、模型名和返回文本路径都做成配置，减少后续因文档差异改代码的概率。
 */
@Service
@RequiredArgsConstructor
public class VolcengineSpeechRecognitionServiceImpl implements SpeechRecognitionService {

    private static final String PROVIDER = "VOLCENGINE_DOUBAO";
    private static final String MODE_DOUBAO_API_KEY = "DOUBAO_API_KEY";
    private static final String MODE_LEGACY_SUBMIT = "LEGACY_SUBMIT";
    private static final String REQUEST_STYLE_FLASH = "FLASH";

    private final VolcengineAsrProperties properties;
    private final ObjectMapper objectMapper;

    @Override
    public SpeechRecognitionResult recognize(byte[] audioBytes, String contentType, String originalFilename) {
        if (!Boolean.TRUE.equals(properties.getEnabled())) {
            return SpeechRecognitionResult.failed(PROVIDER, "ASR 未启用，请配置 job.asr.volcengine.enabled=true");
        }
        if (audioBytes == null || audioBytes.length == 0) {
            return SpeechRecognitionResult.failed(PROVIDER, "音频内容为空");
        }

        try {
            // 1. 有 apiKey 时优先走新版豆包语音；没有 apiKey 才回退旧版 submit。
            if (useDoubaoApiKeyMode()) {
                return recognizeByDoubaoApiKey(audioBytes, contentType, originalFilename);
            }
            return recognizeByLegacySubmit(audioBytes, contentType, originalFilename);
        } catch (Exception exception) {
            return SpeechRecognitionResult.failed(PROVIDER, exception.getMessage());
        }
    }

    private SpeechRecognitionResult recognizeByDoubaoApiKey(byte[] audioBytes, String contentType, String originalFilename) throws Exception {

        System.out.println("=== 进入新版豆包ASR逻辑，endpoint=" + properties.getEndpoint() + " model=" + properties.getModel());

        if (!StringUtils.hasText(properties.getApiKey())) {
            return SpeechRecognitionResult.failed(PROVIDER, "豆包语音 API Key 未配置");
        }
        if (!StringUtils.hasText(properties.getEndpoint())) {
            return SpeechRecognitionResult.failed(PROVIDER, "豆包语音 endpoint 未配置");
        }

        // 1. 录音文件识别 flash 接口使用嵌套 JSON；其它 API Key 接口可切换成 FLAT 扁平结构。
        Map<String, Object> requestBody = buildDoubaoRequestBody(audioBytes, contentType, originalFilename);

        // 2. 构造HTTP请求
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(properties.getEndpoint()))
                .timeout(Duration.ofSeconds(properties.getTimeoutSeconds()))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(requestBody)));

        // 3. 添加鉴权头
        applyApiKeyHeader(builder);

        // ========== 新增：新版豆包语音网关必填请求头 ==========
        // 请求唯一ID，网关路由必须，随机UUID即可
        builder.header("X-Api-Request-Id", UUID.randomUUID().toString());
        // 服务资源ID，对应配置中的model，网关用来路由到具体识别服务
        if (StringUtils.hasText(properties.getResourceId())) {
            builder.header("X-Api-Resource-Id", properties.getResourceId());
        }

        // 4. 发送请求并解析结果
        String responseBody = send(builder.build());
        String text = extractText(responseBody);
        if (!StringUtils.hasText(text)) {
            return SpeechRecognitionResult.failed(PROVIDER, "豆包语音未返回有效识别文本，响应体=" + responseBody
                    + "；请检查 endpoint、API Key 请求头、resourceId、音频格式和 requestStyle 是否与火山文档一致");
        }

        System.out.println("=== ASR请求头列表 ===");
        builder.build().headers().map().forEach((k, v) -> System.out.println(k + ": " + v));
        System.out.println("====================");

        return SpeechRecognitionResult.success(PROVIDER, text.trim());
    }

    private Map<String, Object> buildDoubaoRequestBody(byte[] audioBytes, String contentType, String originalFilename) {
        if (REQUEST_STYLE_FLASH.equalsIgnoreCase(properties.getRequestStyle())) {
            return buildFlashRequestBody(audioBytes, contentType, originalFilename);
        }
        return buildFlatRequestBody(audioBytes, contentType, originalFilename);
    }

    private Map<String, Object> buildFlashRequestBody(byte[] audioBytes, String contentType, String originalFilename) {
        Map<String, Object> requestBody = new LinkedHashMap<>();

        // 1. user.uid 用于服务端追踪请求来源，固定 job-agent 即可。
        requestBody.put("user", Map.of("uid", "job-agent"));

        // 2. flash 录音文件识别通常读取 audio.data/audio.format。
        requestBody.put("audio", Map.of(
                "data", Base64.getEncoder().encodeToString(audioBytes),
                "format", resolveAudioFormat(contentType, originalFilename)
        ));

        // 3. request.model_name 控制使用大模型识别能力；标点、ITN 和分句一起打开，方便面试文本展示。
        requestBody.put("request", Map.of(
                "model_name", StringUtils.hasText(properties.getRequestModelName()) ? properties.getRequestModelName() : "bigmodel",
                "enable_itn", true,
                "enable_punc", true,
                "enable_ddc", false,
                "show_utterances", true
        ));
        return requestBody;
    }

    private Map<String, Object> buildFlatRequestBody(byte[] audioBytes, String contentType, String originalFilename) {
        Map<String, Object> requestBody = new LinkedHashMap<>();
        if (StringUtils.hasText(properties.getModel())) {
            requestBody.put(properties.getModelFieldName(), properties.getModel());
        }
        requestBody.put(properties.getAudioFieldName(), Base64.getEncoder().encodeToString(audioBytes));
        requestBody.put(properties.getFormatFieldName(), resolveAudioFormat(contentType, originalFilename));
        requestBody.put(properties.getLanguageFieldName(), properties.getLanguage());
        return requestBody;
    }

    private SpeechRecognitionResult recognizeByLegacySubmit(byte[] audioBytes, String contentType, String originalFilename) throws Exception {
        if (!StringUtils.hasText(properties.getAppId()) || !StringUtils.hasText(properties.getToken())) {
            return SpeechRecognitionResult.failed(PROVIDER, "旧版 ASR appId/token 未配置；如果你使用新版豆包语音，请配置 api-key");
        }

        // 1. 旧版 submit 请求体沿用火山原有 app/audio/request/data 结构。
        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("app", Map.of(
                "appid", properties.getAppId(),
                "cluster", properties.getCluster(),
                "token", properties.getToken()
        ));
        requestBody.put("user", Map.of("uid", "job-agent"));
        requestBody.put("audio", Map.of(
                "format", resolveAudioFormat(contentType, originalFilename),
                "language", properties.getLanguage()
        ));
        requestBody.put("request", Map.of(
                "reqid", UUID.randomUUID().toString(),
                "workflow", "audio_in,resample,partition,vad,fe,decode,itn,nlu_punctuate"
        ));
        requestBody.put("data", Base64.getEncoder().encodeToString(audioBytes));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(properties.getEndpoint()))
                .timeout(Duration.ofSeconds(properties.getTimeoutSeconds()))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer; " + properties.getToken())
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(requestBody)))
                .build();

        String responseBody = send(request);
        String text = extractText(responseBody);
        if (!StringUtils.hasText(text)) {
            return SpeechRecognitionResult.failed(PROVIDER, "旧版 ASR 未返回有效识别文本: " + responseBody);
        }
        return SpeechRecognitionResult.success(PROVIDER, text.trim());
    }

    private String send(HttpRequest request) throws Exception {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(properties.getTimeoutSeconds()))
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("ASR HTTP " + response.statusCode() + ": " + response.body());
        }
        return response.body();
    }

    private boolean useDoubaoApiKeyMode() {
        if (StringUtils.hasText(properties.getMode())) {
            return MODE_DOUBAO_API_KEY.equalsIgnoreCase(properties.getMode());
        }
        return StringUtils.hasText(properties.getApiKey()) && !MODE_LEGACY_SUBMIT.equalsIgnoreCase(properties.getMode());
    }

    private void applyApiKeyHeader(HttpRequest.Builder builder) {
        String headerName = StringUtils.hasText(properties.getApiKeyHeaderName())
                ? properties.getApiKeyHeaderName()
                : "Authorization";
        String prefix = properties.getApiKeyHeaderPrefix();
        String value = StringUtils.hasText(prefix)
                ? prefix.trim() + " " + properties.getApiKey()
                : properties.getApiKey();
        builder.header(headerName, value);
    }

    private String resolveAudioFormat(String contentType, String originalFilename) {
        String lowerContentType = contentType == null ? "" : contentType.toLowerCase();
        String lowerFilename = originalFilename == null ? "" : originalFilename.toLowerCase();

        if (lowerContentType.contains("wav") || lowerFilename.endsWith(".wav")) {
            return "wav";
        }
        if (lowerContentType.contains("mpeg") || lowerContentType.contains("mp3") || lowerFilename.endsWith(".mp3")) {
            return "mp3";
        }
        if (lowerContentType.contains("ogg") || lowerFilename.endsWith(".ogg")) {
            return "ogg";
        }
        if (lowerContentType.contains("webm") || lowerFilename.endsWith(".webm")) {
            return "webm";
        }
        return "wav";
    }

    private String extractText(String body) throws Exception {
        JsonNode root = objectMapper.readTree(body);
        for (String path : properties.getTextJsonPaths().split(",")) {
            String value = readJsonPath(root, path.trim());
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private String readJsonPath(JsonNode root, String path) {
        if (!StringUtils.hasText(path)) {
            return null;
        }
        JsonNode current = root;
        for (String segment : path.split("\\.")) {
            current = readPathSegment(current, segment);
            if (current == null || current.isMissingNode() || current.isNull()) {
                return null;
            }
        }
        return current.isTextual() ? current.asText() : current.toString();
    }

    private JsonNode readPathSegment(JsonNode node, String segment) {
        int arrayStart = segment.indexOf('[');
        if (arrayStart < 0) {
            return node.path(segment);
        }

        String fieldName = segment.substring(0, arrayStart);
        int arrayEnd = segment.indexOf(']', arrayStart);
        if (arrayEnd < 0) {
            return null;
        }
        int index = Integer.parseInt(segment.substring(arrayStart + 1, arrayEnd));
        JsonNode arrayNode = StringUtils.hasText(fieldName) ? node.path(fieldName) : node;
        return arrayNode.isArray() && arrayNode.size() > index ? arrayNode.get(index) : null;
    }
}
