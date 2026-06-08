package com.job.bootstrap.service;

/**
 * 作者:hfj
 * 功能:Agent Trace 记录服务
 */
public interface AgentTraceService {

    /**
     * 记录 Agent 工具调用日志。
     *
     * @param userId 用户ID
     * @param conversationId 会话ID，可以为空
     * @param intentCode 意图编码
     * @param toolName 工具名称
     * @param input 输入数据
     * @param output 输出数据
     * @param status 状态
     * @param errorMsg 异常信息
     * @param costTime 耗时
     */
    void saveToolTrace(
            Long userId,
            Long conversationId,
            String intentCode,
            String toolName,
            Object input,
            Object output,
            String status,
            String errorMsg,
            Long costTime
    );
}
