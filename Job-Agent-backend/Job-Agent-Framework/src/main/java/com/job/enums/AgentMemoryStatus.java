package com.job.enums;

/**
 * 作者:hfj
 * 功能:Agent 长期记忆状态
 * 日期:2026/6/20
 */
public enum AgentMemoryStatus {

    /**
     * 可被检索和注入 Agent 上下文的有效记忆。
     */
    ACTIVE,

    /**
     * 暂时归档，不参与召回；后续做人工运营后台时可以恢复。
     */
    ARCHIVED,

    /**
     * 已判定不准确或过期，不再参与召回。
     */
    INVALID
}
