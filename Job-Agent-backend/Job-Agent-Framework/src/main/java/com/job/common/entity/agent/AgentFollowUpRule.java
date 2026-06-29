package com.job.common.entity.agent;

import com.baomidou.mybatisplus.annotation.TableName;
import com.job.common.entity.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 求职跟进 Agent 自动规则实体，对应 agent_follow_up_rule 表。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("agent_follow_up_rule")
public class AgentFollowUpRule extends BaseEntity {

    /**
     * 规则编码，全局唯一，用来做幂等标识和运营识别。
     */
    private String ruleCode;

    /**
     * 规则名称，展示给后台管理人员。
     */
    private String ruleName;

    /**
     * 规则类型，例如 INTERVIEW_SCHEDULED、APPLICATION_NO_FEEDBACK。
     */
    private String ruleType;

    /**
     * 触发求职状态，例如 APPLIED、INTERVIEWING。
     */
    private String triggerStatus;

    /**
     * 延迟或提前分钟数。
     * 正数表示事件发生后多少分钟触发，负数表示事件发生前多少分钟触发。
     */
    private Integer delayMinutes;

    /**
     * 创建提醒时使用的提醒类型，例如 INTERVIEW、FOLLOW_UP、CUSTOM。
     */
    private String reminderType;

    /**
     * 创建提醒时使用的标题。
     */
    private String reminderTitle;

    /**
     * 创建提醒时使用的内容模板。
     */
    private String reminderTemplate;

    /**
     * 是否创建邮件通知任务：0 否，1 是。
     */
    private Integer emailEnabled;

    /**
     * 是否创建工作流任务：0 否，1 是。
     */
    private Integer workflowEnabled;

    /**
     * 工作流最大重试次数。
     */
    private Integer maxRetryCount;

    /**
     * 工作流重试间隔秒数。
     */
    private Integer retryIntervalSeconds;

    /**
     * 规则状态：ENABLED / DISABLED。
     */
    private String status;

    /**
     * 管理员备注。
     */
    private String remark;
}
