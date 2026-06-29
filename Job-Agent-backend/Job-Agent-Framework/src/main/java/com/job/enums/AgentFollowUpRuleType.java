package com.job.enums;

/**
 * 求职跟进 Agent 自动规则类型。
 */
public enum AgentFollowUpRuleType {

    /**
     * 用户进入面试中后触发，用来创建面试提醒、面试准备材料和邮件任务。
     */
    INTERVIEW_SCHEDULED,

    /**
     * 投递后长时间没有反馈时触发，用来提醒用户主动跟进 HR。
     */
    APPLICATION_NO_FEEDBACK,

    /**
     * 面试开始前触发，用来提醒用户提前进入准备状态。
     */
    INTERVIEW_BEFORE,

    /**
     * 面试结束后触发，用来提醒用户做面试复盘。
     */
    INTERVIEW_AFTER_REVIEW
}
