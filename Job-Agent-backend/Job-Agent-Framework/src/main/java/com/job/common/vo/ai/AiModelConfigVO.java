package com.job.common.vo.ai;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.job.common.entity.ai.AiModelConfig;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 作者:hfj
 * 功能:AI 模型配置展示 VO
 * 日期:2026/6/21
 */
@Data
public class AiModelConfigVO {

    private Long id;

    private String modelCode;

    private String modelName;

    private String provider;

    private String baseUrl;

    private String apiKey;

    private String chatPath;

    private String modelIdentifier;

    private BigDecimal temperature;

    private Integer maxTokens;

    private Integer timeoutSeconds;

    private Integer maxRetries;

    private BigDecimal inputPricePer1k;

    private BigDecimal outputPricePer1k;

    private Integer circuitEnabled;

    private Integer failureThreshold;

    private Integer cooldownSeconds;

    private String status;

    private String remark;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updateTime;

    /**
     * Entity 转 VO。
     *
     * @param entity 模型配置实体
     * @return 模型配置 VO
     */
    public static AiModelConfigVO from(AiModelConfig entity) {
        if (entity == null) {
            return null;
        }

        AiModelConfigVO vo = new AiModelConfigVO();
        vo.setId(entity.getId());
        vo.setModelCode(entity.getModelCode());
        vo.setModelName(entity.getModelName());
        vo.setProvider(entity.getProvider());
        vo.setBaseUrl(entity.getBaseUrl());
        vo.setApiKey(maskApiKey(entity.getApiKey()));
        vo.setChatPath(entity.getChatPath());
        vo.setModelIdentifier(entity.getModelIdentifier());
        vo.setTemperature(entity.getTemperature());
        vo.setMaxTokens(entity.getMaxTokens());
        vo.setTimeoutSeconds(entity.getTimeoutSeconds());
        vo.setMaxRetries(entity.getMaxRetries());
        vo.setInputPricePer1k(entity.getInputPricePer1k());
        vo.setOutputPricePer1k(entity.getOutputPricePer1k());
        vo.setCircuitEnabled(entity.getCircuitEnabled());
        vo.setFailureThreshold(entity.getFailureThreshold());
        vo.setCooldownSeconds(entity.getCooldownSeconds());
        vo.setStatus(entity.getStatus());
        vo.setRemark(entity.getRemark());
        vo.setCreateTime(entity.getCreateTime());
        vo.setUpdateTime(entity.getUpdateTime());
        return vo;
    }

    /**
     * API Key 脱敏展示。
     *
     * @param apiKey 数据库中的原始 Key
     * @return 脱敏后的 Key
     */
    private static String maskApiKey(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            return null;
        }
        if (apiKey.length() <= 8) {
            return "******";
        }
        return apiKey.substring(0, 4) + "******" + apiKey.substring(apiKey.length() - 4);
    }
}
