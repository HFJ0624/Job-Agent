package com.job.bootstrap.agent.intent;

/**
 * 作者: hfj
 * 功能: Agent 意图编码枚举
 *
 * 设计说明:
 * 1. intentCode 用于标识用户本轮对话想做什么。
 * 2. 后续 trace 日志、工具调用、前端展示都可以根据 intentCode 分类。
 * 3. 第一版先覆盖求职 Agent 的核心场景。
 */
public enum AgentIntentCode {

    /**
     * 普通聊天或求职问答。
     */
    GENERAL_CHAT,

    /**
     * 简历分析。
     */
    RESUME_ANALYZE,

    /**
     * 岗位匹配。
     */
    JOB_MATCH,

    /**
     * 岗位搜索或岗位推荐。
     */
    JOB_SEARCH,

    /**
     * 生成 HR 打招呼语。
     */
    GREETING_GENERATE,

    /**
     * 面试准备。
     */
    INTERVIEW_PREPARE,

    /**
     * 收藏岗位。
     */
    JOB_FAVORITE,

    MOCK_INTERVIEW,
}
