package com.job.common.vo.agent;

import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 功能: 通过率趋势点（概览页折线图）。
 */
@Data
public class EvalTrendPointVO {

    private Long runId;
    private Date time;
    /** 通过率百分比（0-100）。 */
    private BigDecimal passRate;
}
