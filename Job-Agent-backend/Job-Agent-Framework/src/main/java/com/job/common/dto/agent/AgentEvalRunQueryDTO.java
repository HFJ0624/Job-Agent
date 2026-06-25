package com.job.common.dto.agent;

import lombok.Data;

/**
 * 作者:hfj
 * 功能:Agent Eval 运行批次分页查询参数
 * 日期:2026/6/24
 */
@Data
public class AgentEvalRunQueryDTO {
    private Long pageNum = 1L;
    private Long pageSize = 10L;
    private Long datasetId;
    private String runType;
    private String status;
}
