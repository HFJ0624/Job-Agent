package com.job.enums;

/**
 * 作者: hfj
 * 功能: Agent 长期记忆动作类型
 * 日期: 2026/6/23
 *
 * 说明:
 * 1. 长期记忆不是看到关键词就写入，而是先判断用户这句话对记忆库的“动作意图”。
 * 2. 只有 SET_MEMORY 和 UPDATE_MEMORY 会进入抽取和写入流程。
 * 3. ASK_MEMORY 只允许召回已有记忆，不允许把问题本身写成新记忆。
 * 4. DELETE_MEMORY 用来归档指定记忆，避免用户要求“别记住”后旧记忆继续污染 Prompt。
 */
public enum AgentMemoryActionType {

    /**
     * 用户明确设置一条长期记忆，例如“以后叫我老黄”。
     */
    SET_MEMORY,

    /**
     * 用户明确修改已有长期记忆，例如“以后不要叫我老黄，叫我老王”。
     */
    UPDATE_MEMORY,

    /**
     * 用户要求删除或忘记某类长期记忆，例如“别记住我的名字”。
     */
    DELETE_MEMORY,

    /**
     * 用户在询问已有记忆，例如“你还记得我叫什么吗”。
     */
    ASK_MEMORY,

    /**
     * 普通聊天或一次性任务，不进入长期记忆写入流程。
     */
    NORMAL_CHAT
}
