package com.job.enums;

/**
 * 作者:hfj
 * 功能:Agent 工具统一错误码
 * 日期:2026/6/20
 */
public enum AgentToolErrorCode {

    /**
     * 工具未在 Schema 注册中心登记。
     */
    TOOL_NOT_REGISTERED,

    /**
     * 工具执行时缺少 AgentRuntimeContext。
     */
    TOOL_CONTEXT_MISSING,

    /**
     * 工具缺少必填入参。
     */
    TOOL_PARAM_MISSING,

    /**
     * 当前运行身份没有调用该工具的权限。
     */
    TOOL_PERMISSION_DENIED,

    /**
     * 工具需要用户确认，但本轮请求没有携带确认信息。
     */
    TOOL_CONFIRMATION_REQUIRED,

    /**
     * 工具内部业务执行失败。
     */
    TOOL_EXECUTION_FAILED
}
