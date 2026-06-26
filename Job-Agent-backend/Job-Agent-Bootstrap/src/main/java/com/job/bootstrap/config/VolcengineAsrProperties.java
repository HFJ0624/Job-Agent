package com.job.bootstrap.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 功能: 火山引擎/豆包语音 ASR 配置。
 *
 * 说明:
 * 1. 新版豆包语音优先使用 API Key 接入，适合你当前控制台创建的 API Key。
 * 2. 旧版火山 ASR 的 appId/token/cluster 仍保留，避免老配置直接失效。
 * 3. 新版豆包语音不同产品线的字段名可能略有差异，所以把请求字段和返回文本路径做成配置。
 */
@Data
@ConfigurationProperties(prefix = "job.asr.volcengine")
public class VolcengineAsrProperties {

    /**
     * 是否启用 ASR。
     */
    private Boolean enabled = false;

    /**
     * 接入模式: DOUBAO_API_KEY / LEGACY_SUBMIT。
     * 为空时，如果配置了 apiKey 就自动使用 DOUBAO_API_KEY，否则使用 LEGACY_SUBMIT。
     */
    private String mode;

    /**
     * 新版豆包语音 API Key。
     */
    private String apiKey;

    /**
     * 新版豆包语音接口地址，请从控制台“录音文件识别 - API 接入”复制。
     */
    private String endpoint = "https://openspeech.bytedance.com/api/v3/auc/bigmodel/recognize/flash";

    /**
     * API Key 请求头名称。多数新版接口使用 Authorization。
     */
    private String apiKeyHeaderName = "Authorization";

    /**
     * API Key 请求头前缀。常见为 Bearer；如果控制台要求直接传 key，可配置为空字符串。
     */
    private String apiKeyHeaderPrefix = "Bearer";

    /**
     * 新版接口使用的模型或服务标识。具体值以控制台 API 接入页为准。
     */
    private String model = "volc.bigasr.auc";

    /**
     * 请求体风格: FLASH / FLAT。
     * FLASH: 适配 /api/v3/auc/bigmodel/recognize/flash 这类录音文件识别接口。
     * FLAT: 保留上一版扁平 JSON 结构，方便兼容其它 API Key 接口。
     */
    private String requestStyle = "FLASH";

    /**
     * 豆包语音网关资源 ID，通常放在 X-Api-Resource-Id 请求头。
     */
    private String resourceId = "volc.bigasr.auc";

    /**
     * flash 接口 request.model_name 字段。
     */
    private String requestModelName = "bigmodel";

    /**
     * 音频 base64 字段名。若文档要求 input/audio/data，可通过配置覆盖。
     */
    private String audioFieldName = "audio_data";

    /**
     * 音频格式字段名。
     */
    private String formatFieldName = "audio_format";

    /**
     * 模型字段名。
     */
    private String modelFieldName = "model";

    /**
     * 语言字段名。
     */
    private String languageFieldName = "language";

    /**
     * 新版接口返回文本路径，多个路径用英文逗号隔开。
     * 例如: text,result.text,result[0].text,data.text,choices[0].message.content
     */
    private String textJsonPaths = "text,result.text,result[0].text,result.utterances[0].text,data.text,data.result.text,data.result.utterances[0].text,choices[0].message.content";

    /**
     * 旧版火山引擎应用 AppID。
     */
    private String appId;

    /**
     * 旧版火山引擎 ASR Token。
     */
    private String token;

    /**
     * 旧版识别集群。
     */
    private String cluster = "volcengine_streaming_common";

    /**
     * 识别语言。
     */
    private String language = "zh-CN";

    /**
     * 网络超时时间。
     */
    private Integer timeoutSeconds = 30;
}
