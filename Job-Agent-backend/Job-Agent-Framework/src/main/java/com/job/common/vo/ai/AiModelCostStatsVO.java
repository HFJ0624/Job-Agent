package com.job.common.vo.ai;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 作者:hfj
 * 功能:AI 模型 token 与成本统计 VO
 * 日期:2026/6/21
 */
@Data
public class AiModelCostStatsVO {

    /**
     * 总调用次数。
     */
    private Long totalCalls = 0L;

    /**
     * 成功次数。
     */
    private Long successCalls = 0L;

    /**
     * 失败次数。
     */
    private Long failedCalls = 0L;

    /**
     * 总 token。
     */
    private Long totalTokens = 0L;

    /**
     * 总成本。
     */
    private BigDecimal totalCost = BigDecimal.ZERO;

    /**
     * 平均耗时，单位毫秒。
     */
    private Long avgCostTime = 0L;
}
