package com.job.common.dto.ai;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 作者:hfj
 * 功能:AI 模型配置保存参数
 * 日期:2026/6/21
 */
@Data
public class AiModelConfigSaveDTO {

    /**
     * 模型编码，运行时路由通过它定位模型。
     */
    @NotBlank(message = "模型编码不能为空")
    private String modelCode;

    /**
     * 后台展示名称。
     */
    @NotBlank(message = "模型名称不能为空")
    private String modelName;

    /**
     * 供应商，第一版按 OpenAI 兼容接口调用。
     */
    @NotBlank(message = "供应商不能为空")
    private String provider;

    /**
     * OpenAI 兼容接口 BaseUrl，例如 https://api.openai.com/v1。
     */
    @NotBlank(message = "BaseUrl 不能为空")
    private String baseUrl;

    /**
     * 模型 API Key。
     * 第一版按用户确认明文入库；编辑时如果为空或是脱敏值，会保留数据库原值。
     */
    private String apiKey;

    /**
     * 聊天接口路径，默认 /chat/completions。
     */
    private String chatPath;

    /**
     * 供应商真实模型名，例如 gpt-4.1-mini、deepseek-chat。
     */
    @NotBlank(message = "模型标识不能为空")
    private String modelIdentifier;

    /**
     * 温度。
     */
    private BigDecimal temperature;

    /**
     * 最大输出 token。
     */
    private Integer maxTokens;

    /**
     * 请求超时时间，单位秒。
     */
    private Integer timeoutSeconds;

    /**
     * 失败后的最大重试次数。
     */
    private Integer maxRetries;

    /**
     * 输入 token 单价，单位为每 1000 token。
     */
    private BigDecimal inputPricePer1k;

    /**
     * 输出 token 单价，单位为每 1000 token。
     */
    private BigDecimal outputPricePer1k;

    /**
     * 是否启用熔断，1 表示启用，0 表示关闭。
     */
    private Integer circuitEnabled;

    /**
     * 连续失败多少次后打开熔断。
     */
    private Integer failureThreshold;

    /**
     * 熔断冷却时间，单位秒。
     */
    private Integer cooldownSeconds;

    /**
     * 配置状态。
     */
    private String status;

    /**
     * 管理员备注。
     */
    private String remark;
}
