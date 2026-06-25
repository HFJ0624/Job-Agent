package com.job.common.dto.agent;

import lombok.Data;

/**
 * 作者:hfj
 * 功能:Agent Eval 数据集保存参数
 * 日期:2026/6/24
 */
@Data
public class AgentEvalDatasetSaveDTO {
    private String datasetName;
    private String datasetCode;
    private String description;
    private String evalType;
    private Integer enableStatus;
    private String remark;
}
