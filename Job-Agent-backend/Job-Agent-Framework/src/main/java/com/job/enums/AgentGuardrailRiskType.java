package com.job.enums;

/**
 * 作者:hfj
 * 功能:Agent 护栏风险类型
 * 日期:2026/6/21
 */
public enum AgentGuardrailRiskType {

    /**
     * 未发现风险。
     */
    NONE,

    /**
     * Prompt 注入或越狱指令。
     */
    PROMPT_INJECTION,

    /**
     * 工具调用越权。
     */
    TOOL_ACCESS_DENIED,

    /**
     * 敏感操作未确认。
     */
    SENSITIVE_OPERATION,

    /**
     * 工具输出不是合法 JSON。
     */
    TOOL_OUTPUT_INVALID_JSON,

    /**
     * 工具输出和 Schema 不一致。
     */
    TOOL_OUTPUT_SCHEMA_MISMATCH,

    /**
     * 回复可能包含无依据结论。
     */
    HALLUCINATION_RISK,

    /**
     * 检测到个人敏感信息。
     */
    PII_DETECTED
}
