package com.job.bootstrap.service;

import com.job.common.vo.agent.AgentConversationVO;
import com.job.common.vo.agent.AgentMessageVO;

import java.util.List;

/**
 * 作者:hfj
 * 功能:AI 助手历史会话服务
 */
public interface AgentHistoryService {

    /**
     * 查询当前用户的会话列表。
     *
     * @param userId 当前登录用户ID
     * @return 会话列表
     */
    List<AgentConversationVO> listConversations(Long userId);

    /**
     * 查询当前用户某个会话下的消息列表。
     *
     * @param userId 当前登录用户ID
     * @param conversationId 会话ID
     * @return 消息列表
     */
    List<AgentMessageVO> listMessages(Long userId, Long conversationId);

    /**
     * 删除当前用户的某个会话。
     *
     * @param userId 当前登录用户ID
     * @param conversationId 会话ID
     */
    void deleteConversation(Long userId, Long conversationId);
}
