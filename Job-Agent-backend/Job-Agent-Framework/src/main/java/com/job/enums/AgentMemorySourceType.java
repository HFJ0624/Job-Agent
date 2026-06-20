package com.job.enums;

/**
 * 作者:hfj
 * 功能:Agent 长期记忆来源类型
 * 日期:2026/6/20
 *
 * 说明:
 * sourceType 用来回答“这条记忆从哪里来”，便于后台管理人员排查记忆质量。
 */
public enum AgentMemorySourceType {

    /**
     * 来自用户自然语言输入。
     */
    USER_MESSAGE,

    /**
     * 来自 Planner 抽取出来的结构化参数。
     */
    AGENT_PLAN,

    /**
     * 来自某个工具的执行结果。
     */
    TOOL_RESULT,

    /**
     * 后台人工录入或人工修正。
     */
    ADMIN_MANUAL
}
