package com.job.enums;

/**
 * 作者: hfj
 * 功能: Agent 统一观测事件状态
 * 日期: 2026/6/22
 */
public enum AgentObservationStatus {

    /**
     * 执行成功。
     */
    SUCCESS,

    /**
     * 执行失败。
     */
    FAILED,

    /**
     * 被安全规则或权限规则拦截。
     */
    BLOCKED,

    /**
     * 被 Executor 主动跳过。
     */
    SKIPPED
}
