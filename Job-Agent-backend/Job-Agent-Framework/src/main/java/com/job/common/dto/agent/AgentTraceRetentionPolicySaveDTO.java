package com.job.common.dto.agent;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 作者: hfj
 * 功能: Agent Trace 保留策略保存参数
 * 日期: 2026/6/22
 */
@Data
public class AgentTraceRetentionPolicySaveDTO {

    @NotBlank(message = "策略名称不能为空")
    private String policyName;

    @NotBlank(message = "目标表不能为空")
    private String targetTable;

    @NotNull(message = "保留天数不能为空")
    private Integer retentionDays;

    private Integer batchSize;

    private String status;

    private String remark;
}
