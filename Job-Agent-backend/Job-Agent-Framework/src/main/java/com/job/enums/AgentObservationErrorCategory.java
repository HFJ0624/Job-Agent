package com.job.enums;

/**
 * 作者: hfj
 * 功能: Agent 统一失败分类
 * 日期: 2026/6/22
 */
public enum AgentObservationErrorCategory {

    /**
     * 没有失败。
     */
    NONE,

    /**
     * 配置缺失或配置错误。
     */
    CONFIG_ERROR,

    /**
     * 模型网关、供应商接口或模型响应异常。
     */
    MODEL_ERROR,

    /**
     * 工具执行失败。
     */
    TOOL_ERROR,

    /**
     * 工具需要用户确认。
     */
    TOOL_CONFIRMATION,

    /**
     * Guardrails 拦截。
     */
    GUARDRAIL_BLOCKED,

    /**
     * RAG 检索或索引异常。
     */
    RAG_ERROR,

    /**
     * 请求超时。
     */
    TIMEOUT,

    /**
     * 权限不足。
     */
    PERMISSION_DENIED,

    /**
     * 参数缺失。
     */
    PARAM_MISSING,

    /**
     * 无法归类的系统异常。
     */
    SYSTEM_ERROR
}
