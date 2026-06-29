package com.job.enums;

/**
 * 求职跟进 Agent 规则状态。
 */
public enum AgentFollowUpRuleStatus {

    /**
     * 启用：规则会参与事件触发和定时扫描。
     */
    ENABLED,

    /**
     * 禁用：规则只保留配置，不会被执行。
     */
    DISABLED
}
