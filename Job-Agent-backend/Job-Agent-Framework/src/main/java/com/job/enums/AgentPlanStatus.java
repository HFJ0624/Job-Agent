package com.job.enums;

/**
 * 作者:hfj
 * 功能:Agent 计划状态
 * 日期:2026/6/19
 */
public enum AgentPlanStatus {

    /**
     * 已生成计划，可以进入 Agent 执行阶段。
     */
    PLANNED,

    /**
     * 缺少必要参数，需要用户补充信息。
     */
    NEED_CLARIFICATION,

    /**
     * 已完成。
     */
    COMPLETED,

    /**
     * 计划失败。
     */
    FAILED
}
