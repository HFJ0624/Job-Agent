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
}
