package com.job.common.dto.agent;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 求职跟进 Agent 规则保存参数。
 */
@Data
public class AgentFollowUpRuleSaveDTO {

    @NotBlank(message = "规则编码不能为空")
    private String ruleCode;

    @NotBlank(message = "规则名称不能为空")
    private String ruleName;

    @NotBlank(message = "规则类型不能为空")
    private String ruleType;

    private String triggerStatus;

    private Integer delayMinutes;

    private String reminderType;

    private String reminderTitle;

    private String reminderTemplate;

    private Integer emailEnabled;

    private Integer workflowEnabled;

    private Integer maxRetryCount;

    private Integer retryIntervalSeconds;

    private String status;

    private String remark;
}
