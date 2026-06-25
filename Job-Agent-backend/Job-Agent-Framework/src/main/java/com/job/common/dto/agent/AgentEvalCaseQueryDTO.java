package com.job.common.dto.agent;

import lombok.Data;

/**
 * 作者:hfj
 * 功能:Agent Eval 用例分页查询参数
 * 日期:2026/6/24
 */
@Data
public class AgentEvalCaseQueryDTO {
    private Long pageNum = 1L;
    private Long pageSize = 10L;
    private Long datasetId;
    private String caseName;
    private Long userId;
    private String evalType;
    private String expectedToolName;
    private Integer enableStatus;
}
