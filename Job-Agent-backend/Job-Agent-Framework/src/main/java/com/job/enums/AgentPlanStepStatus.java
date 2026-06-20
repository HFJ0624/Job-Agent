package com.job.enums;

/**
 * 作者:hfj
 * 功能:Agent 计划步骤状态
 * 日期:2026/6/19
 */
public enum AgentPlanStepStatus {

    /**
     * 等待执行。
     */
    PENDING,

    /**
     * 执行中。
     */
    RUNNING,

    /**
     * 已完成。
     */
    COMPLETED,

    /**
     * 已跳过。
     */
    SKIPPED,

    /**
     * 执行失败。
     */
    FAILED
}
