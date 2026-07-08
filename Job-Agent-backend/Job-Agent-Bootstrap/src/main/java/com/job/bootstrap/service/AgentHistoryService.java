package com.job.bootstrap.service;

import com.job.common.vo.agent.AgentConversationVO;
import com.job.common.vo.agent.AgentMessageVO;

import java.util.List;

/**
 * AI 助手历史会话服务接口。
 *
 * <p>核心职责：管理用户与 AI 助手的历史会话及消息记录，支持会话列表查询、消息回溯和会话删除。</p>
 *
 * <p>所属业务模块：AI 助手 - 对话（Chat）</p>
 *
 * <p>主要调用链：
 * AgentHistoryController -&gt; AgentHistoryService -&gt; AgentHistoryServiceImpl -&gt; AgentConversationRepository / AgentMessageRepository</p>
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
