package com.job.common.vo.agent;

import lombok.Data;

/**
 * 功能: 批次指标快照（由逐用例结果重算，用于与 run 聚合值交叉校验）。
 */
@Data
public class EvalMetricsVO {

    private Double passRate;
    private Double toolAccuracy;
    private Double paramAccuracy;
    private Double ragHitRate;
    private Double answerRate;
    private Long avgCostTime;
}
