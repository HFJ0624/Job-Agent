package com.job.common.entity.ai;

import com.baomidou.mybatisplus.annotation.TableName;
import com.job.common.entity.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 作者:hfj
 * 功能:AI 模型调用日志实体
 * 日期:2026/6/21
 *
 * 说明:
 * 1. 每次模型调用都落一条日志，便于后台统计耗时、token 和成本。
 * 2. token 第一版使用字符数估算，后续可替换为供应商返回的真实 usage。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_model_call_log")
public class AiModelCallLog extends BaseEntity {

    private String traceId;

    private Long userId;

    private String sceneCode;

    private String promptCode;

    private Long promptVersionId;

    private String modelCode;

    private Integer fallbackUsed;

    private Integer inputTokens;

    private Integer outputTokens;

    private Integer totalTokens;

    private BigDecimal inputCost;

    private BigDecimal outputCost;

    private BigDecimal totalCost;

    private Long costTime;

    private String status;

    private String errorMsg;
}
