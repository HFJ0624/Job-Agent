package com.job.common.dto.agent;

import lombok.Data;

/**
 * 作者:hfj
 * 功能:Agent Eval 结果分页查询参数
 * 日期:2026/6/24
 */
@Data
public class AgentEvalResultQueryDTO {
    private Long pageNum = 1L;
    private Long pageSize = 10L;
    private Long runId;
    private Long datasetId;
    private Long caseId;
    private String evalType;
    private Integer passStatus;
    private String failureType;
}
