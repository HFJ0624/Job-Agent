package com.job.common.vo.agent;

import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/**
 * 功能: 评测概览（首页四张指标卡 + 趋势 + 失败 Top + 最近批次）。
 *
 * <p>只给"一眼能看懂"的聚合结果，逐用例明细在批次报告接口里。</p>
 */
@Data
public class EvalOverviewVO {

    private Long datasetId;
    /** 数据集里的用例总数。 */
    private Integer totalCases;
    /** 最近一次完成的批次；从未跑过为 null。 */
    private AgentEvalRunVO latestRun;
    /** 最近批次的通过率（0-100）。 */
    private BigDecimal passRate;
    /** 按时间正序的趋势点。 */
    private List<EvalTrendPointVO> trend;
    /** 失败原因 Top（最多 4 个）。 */
    private List<EvalFailureCategoryVO> failureTop;
    /** 最近批次的失败用例数。 */
    private Integer failCount;
    private Date lastRunTime;
}
