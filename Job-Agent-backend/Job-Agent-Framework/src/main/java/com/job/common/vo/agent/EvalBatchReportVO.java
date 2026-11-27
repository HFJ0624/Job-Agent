package com.job.common.vo.agent;

import lombok.Data;

import java.util.List;

/**
 * 功能: 单个评测批次报告（指标 + 失败分类 + 逐用例结果）。
 */
@Data
public class EvalBatchReportVO {

    private AgentEvalRunVO run;
    private List<EvalFailureCategoryVO> failureCategories;
    private List<AgentEvalResultVO> cases;
    private Integer caseTotal;

    /** 由逐用例结果重算的指标；为空表示尚未实现（前端回退到 run 聚合值）。 */
    private EvalMetricsVO metrics;

    /** 门禁结果；为空表示尚未实现（不展示 PASS/BLOCK）。 */
    private Boolean gatePass;
    private String gateReason;
    private Double passRateDelta;
    private Double toolDelta;
    private Double paramDelta;
}
