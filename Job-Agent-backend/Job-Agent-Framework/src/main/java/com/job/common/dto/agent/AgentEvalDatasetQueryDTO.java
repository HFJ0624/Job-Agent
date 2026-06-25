package com.job.common.dto.agent;

import lombok.Data;

/**
 * 作者:hfj
 * 功能:Agent Eval 数据集分页查询参数
 * 日期:2026/6/24
 */
@Data
public class AgentEvalDatasetQueryDTO {
    private Long pageNum = 1L;
    private Long pageSize = 10L;
    private String datasetName;
    private String datasetCode;
    private String evalType;
    private Integer enableStatus;
}
