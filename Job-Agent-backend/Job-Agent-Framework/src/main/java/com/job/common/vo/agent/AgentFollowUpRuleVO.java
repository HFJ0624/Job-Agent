package com.job.common.vo.agent;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.job.common.entity.agent.AgentFollowUpRule;
import lombok.Data;

import java.util.Date;

/**
 * 求职跟进 Agent 规则展示对象。
 */
@Data
public class AgentFollowUpRuleVO {

    private Long id;

    private String ruleCode;

    private String ruleName;

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

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updateTime;

    public static AgentFollowUpRuleVO from(AgentFollowUpRule entity) {
        if (entity == null) {
            return null;
        }
        AgentFollowUpRuleVO vo = new AgentFollowUpRuleVO();
        vo.setId(entity.getId());
        vo.setRuleCode(entity.getRuleCode());
        vo.setRuleName(entity.getRuleName());
        vo.setRuleType(entity.getRuleType());
        vo.setTriggerStatus(entity.getTriggerStatus());
        vo.setDelayMinutes(entity.getDelayMinutes());
        vo.setReminderType(entity.getReminderType());
        vo.setReminderTitle(entity.getReminderTitle());
        vo.setReminderTemplate(entity.getReminderTemplate());
        vo.setEmailEnabled(entity.getEmailEnabled());
        vo.setWorkflowEnabled(entity.getWorkflowEnabled());
        vo.setMaxRetryCount(entity.getMaxRetryCount());
        vo.setRetryIntervalSeconds(entity.getRetryIntervalSeconds());
        vo.setStatus(entity.getStatus());
        vo.setRemark(entity.getRemark());
        vo.setCreateTime(entity.getCreateTime());
        vo.setUpdateTime(entity.getUpdateTime());
        return vo;
    }
}
