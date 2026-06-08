package com.job.bootstrap.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.job.bootstrap.mapper.AiConversationMapper;
import com.job.bootstrap.mapper.AiMessageMapper;
import com.job.bootstrap.service.AgentHistoryService;
import com.job.common.entity.agent.AiConversation;
import com.job.common.entity.agent.AiMessage;
import com.job.common.vo.agent.AgentConversationVO;
import com.job.common.vo.agent.AgentMessageVO;
import com.job.exception.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
/**
 * 作者:hfj
 * 功能:AI 助手历史会话服务实现
 * 日期: 2026/6/8 19:49
 */
@Service
@RequiredArgsConstructor
public class AgentHistoryServiceImpl implements AgentHistoryService {

    private static final int NOT_DELETED = 0;
    private static final int DELETED = 1;

    private final AiConversationMapper aiConversationMapper;
    private final AiMessageMapper aiMessageMapper;

    /**
     * 查询当前用户最近的会话列表。
     */
    @Override
    public List<AgentConversationVO> listConversations(Long userId) {
        List<AiConversation> conversations = aiConversationMapper.selectList(
                new LambdaQueryWrapper<AiConversation>()
                        .eq(AiConversation::getUserId, userId)
                        .eq(AiConversation::getIsDeleted, NOT_DELETED)
                        .orderByDesc(AiConversation::getUpdateTime)
                        .last("limit 50")
        );

        return conversations.stream()
                .map(AgentConversationVO::from)
                .toList();
    }

    /**
     * 查询某个会话下的消息。
     */
    @Override
    public List<AgentMessageVO> listMessages(Long userId, Long conversationId) {
        AiConversation conversation = aiConversationMapper.selectById(conversationId);

        /*
         * 必须校验会话归属，避免用户通过改 conversationId 查看别人的聊天记录。
         */
        if (conversation == null
                || !userId.equals(conversation.getUserId())
                || DELETED == conversation.getIsDeleted()) {
            throw new BizException("会话不存在或无权限访问");
        }

        List<AiMessage> messages = aiMessageMapper.selectList(
                new LambdaQueryWrapper<AiMessage>()
                        .eq(AiMessage::getConversationId, conversationId)
                        .eq(AiMessage::getUserId, userId)
                        .eq(AiMessage::getIsDeleted, NOT_DELETED)
                        .orderByAsc(AiMessage::getCreateTime)
        );

        return messages.stream()
                .map(AgentMessageVO::from)
                .toList();
    }

    /**
     * 删除会话。
     *
     * 说明:
     * 1. 这里使用逻辑删除。
     * 2. 会话删除后，消息也同步逻辑删除。
     */
    @Override
    public void deleteConversation(Long userId, Long conversationId) {
        AiConversation conversation = aiConversationMapper.selectById(conversationId);

        if (conversation == null
                || !userId.equals(conversation.getUserId())
                || DELETED == conversation.getIsDeleted()) {
            throw new BizException("会话不存在或无权限删除");
        }

        /*
         * 删除会话。
         */
        conversation.setIsDeleted(DELETED);
        aiConversationMapper.updateById(conversation);

        /*
         * 删除会话下的消息。
         */
        List<AiMessage> messages = aiMessageMapper.selectList(
                new LambdaQueryWrapper<AiMessage>()
                        .eq(AiMessage::getConversationId, conversationId)
                        .eq(AiMessage::getUserId, userId)
                        .eq(AiMessage::getIsDeleted, NOT_DELETED)
        );

        for (AiMessage message : messages) {
            message.setIsDeleted(DELETED);
            aiMessageMapper.updateById(message);
        }
    }
}
