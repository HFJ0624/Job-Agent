package com.job.enums;

/**
 * 作者:hfj
 * 功能:Agent 护栏处理动作
 * 日期:2026/6/21
 */
public enum AgentGuardrailAction {

    /**
     * 放行。
     */
    ALLOW,

    /**
     * 放行但记录风险。
     */
    WARN,

    /**
     * 直接拦截。
     */
    BLOCK
}
