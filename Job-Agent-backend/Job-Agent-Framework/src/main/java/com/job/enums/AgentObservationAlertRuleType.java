package com.job.enums;

/**
 * 作者: hfj
 * 功能: Agent 观测告警规则类型
 * 日期: 2026/6/22
 */
public enum AgentObservationAlertRuleType {

    /**
     * 失败率超过阈值。
     */
    FAILURE_RATE,

    /**
     * 指定失败分类次数超过阈值。
     */
    ERROR_CATEGORY_COUNT,

    /**
     * 平均耗时超过阈值。
     */
    AVG_DURATION,

    /**
     * token 成本超过阈值。
     */
    TOTAL_COST,

    /**
     * Guardrails 拦截次数超过阈值。
     */
    GUARDRAIL_BLOCK_COUNT
}
