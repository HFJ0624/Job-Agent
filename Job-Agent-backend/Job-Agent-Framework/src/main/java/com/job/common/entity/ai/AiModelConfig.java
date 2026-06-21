package com.job.common.entity.ai;

import com.baomidou.mybatisplus.annotation.TableName;
import com.job.common.entity.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 作者:hfj
 * 功能:AI 模型配置实体
 * 日期:2026/6/21
 *
 * 说明:
 * 1. 这张表让管理员在后台维护模型供应商、模型名称、超时、重试和价格。
 * 2. 运行时模型网关会根据 modelCode 读取本表，而不是把模型参数写死在代码里。
 * 3. apiKey 第一版按用户确认明文入库，接口返回给前端时会做脱敏展示。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_model_config")
public class AiModelConfig extends BaseEntity {

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
}
