package com.job.enums;

/**
 * 作者:hfj
 * 功能:Agent 工具确认策略
 * 日期:2026/6/20
 */
public enum AgentToolConfirmationType {

    /**
     * 不需要用户二次确认。
     */
    NONE,

    /**
     * 工具执行前必须有用户确认。
     */
    REQUIRED_BEFORE_EXECUTION
}
