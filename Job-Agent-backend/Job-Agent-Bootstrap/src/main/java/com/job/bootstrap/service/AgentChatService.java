package com.job.bootstrap.service;

import com.job.common.vo.agent.AgentChatVO;

import java.util.List;

/**
 * 作者:hfj
 * 功能:AI 助手聊天服务
 */
public interface AgentChatService {

    /**
     * 和 AI 助手对话。
     *
     * @param userId 当前用户ID
     * @param conversationId 会话ID，可以为空
     * @param message 用户消息
     * @param confirmedToolNames 本轮用户已确认允许执行的工具名
     * @return 助手回复
     */
    AgentChatVO chat(Long userId, Long conversationId, String message, List<String> confirmedToolNames);
}
