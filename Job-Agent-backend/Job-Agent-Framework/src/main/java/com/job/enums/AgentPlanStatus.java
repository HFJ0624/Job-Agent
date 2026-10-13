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
    FAILED,

    /**
     * 正在执行中。
     *
     * 说明:
     * 1. 该状态由 Executor 执行前通过条件更新抢占设置，用于防止同一计划被并发重复执行。
     * 2. status 列是字符串，新增枚举值不需要修改表结构。
     * 3. 如果进程崩溃导致计划一直停留在 RUNNING，Executor 会按 update_time 超时判定为残留并允许重新抢占。
     */
    RUNNING
}
