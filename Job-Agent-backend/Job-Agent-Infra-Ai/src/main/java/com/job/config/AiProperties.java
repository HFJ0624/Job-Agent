package com.job.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
/**
 * 作者:hfj
 * 功能:AI 模型配置属性
 * 日期: 2026/6/8 14:59
 */
@Data
@Component
@ConfigurationProperties(prefix = "job.ai")
public class AiProperties {

    /**
     * 大模型 API Key。
     */
    private String apiKey;

    /**
     * OpenAI 兼容接口地址。
     */
    private String baseUrl;

    /**
     * 模型名称。
     */
    private String modelName;

    /**
     * 温度参数，越低越稳定。
     */
    private Double temperature = 0.3;
}
