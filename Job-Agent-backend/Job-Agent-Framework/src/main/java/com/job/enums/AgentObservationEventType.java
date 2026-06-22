package com.job.enums;

/**
 * 作者: hfj
 * 功能: Agent 统一观测事件类型
 * 日期: 2026/6/22
 */
public enum AgentObservationEventType {

    /**
     * 模型调用事件。
     */
    MODEL,

    /**
     * 工具调用事件。
     */
    TOOL,

    /**
     * Executor 步骤执行事件。
     */
    EXECUTOR,

    /**
     * Guardrails 拦截事件。
     */
    GUARDRAIL,

    /**
     * RAG 检索事件。
     */
    RAG,

    /**
     * 普通 Trace 事件。
     */
    TRACE
}
