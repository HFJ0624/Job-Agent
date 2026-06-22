package com.job.common.dto.agent;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 作者: hfj
 * 功能: Agent 观测告警规则保存参数
 * 日期: 2026/6/22
 */
@Data
public class AgentObservationAlertRuleSaveDTO {

    @NotBlank(message = "规则名称不能为空")
    private String ruleName;

    @NotBlank(message = "规则类型不能为空")
    private String ruleType;

    private String eventType;

    private String errorCategory;

    private String modelCode;

    private String toolName;

    @NotNull(message = "阈值不能为空")
    private BigDecimal thresholdValue;

    private Integer windowMinutes;

    private Integer minSampleCount;

    private Integer cooldownMinutes;

    private String alertLevel;

    private String status;

    private String remark;
}
