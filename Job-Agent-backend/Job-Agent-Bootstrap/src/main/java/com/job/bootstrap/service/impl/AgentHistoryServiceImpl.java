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
 * AI 助手历史会话服务实现，负责会话列表查询、消息查询与会话逻辑删除。
 *
 * <p>核心职责：
 * 提供前端历史会话入口所需的数据：列出当前用户最近 50 条会话、查询指定会话下的全部消息、
 * 逻辑删除会话及其下消息。所有操作都校验 userId 归属，避免用户通过篡改 conversationId
 * 查看或删除他人聊天记录。</p>
 *
 * <p>所属业务模块：Job-Agent-Bootstrap 模块下的 Agent History 子模块（历史会话访问层）。</p>
 *
 * <p>主要调用链：
 * 前端历史会话页 -> AgentHistoryService.listConversations / listMessages / deleteConversation
 * -> AiConversationMapper / AiMessageMapper</p>
 *
 * <p>与其他核心组件的关系：
 * <ul>
 *   <li>会话与消息由 AgentChatServiceImpl 在主链路中落库；</li>
 *   <li>本服务只读 + 逻辑删除，不写新的会话或消息；</li>
 *   <li>使用逻辑删除（is_deleted=1）保留数据用于审计与 Trace 排查。</li>
 * </ul></p>
 *
 * 作者: hfj
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
     * 查询当前用户最近的会话列表，按 updateTime 倒序最多返回 50 条。
     *
     * @param userId 当前用户 ID，用于过滤归属
     * @return 会话 VO 列表，按最近更新时间倒序
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
     * 查询指定会话下的全部消息，按 createTime 升序排列。
     *
     * <p>核心处理流程：
     * 1. 按 conversationId 查询会话实体；
     * 2. 校验会话存在、归属当前用户且未删除，避免越权访问他人聊天记录；
     * 3. 查询该会话下未删除消息，按时间升序保证对话顺序。</p>
     *
     * @param userId         当前用户 ID，用于权限校验
     * @param conversationId 会话 ID
     * @return 消息 VO 列表，按 createTime 升序
     * @throws BizException 会话不存在、不归属当前用户或已删除时抛出
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
     * 逻辑删除会话及其下全部消息，保留数据用于审计与 Trace 排查。
     *
     * <p>核心处理流程：
     * 1. 按 conversationId 查询会话实体；
     * 2. 校验会话存在、归属当前用户且未删除，避免越权删除他人会话；
     * 3. 把会话 is_deleted 标记为 1；
     * 4. 查询该会话下未删除消息，逐条标记 is_deleted=1，避免脏数据残留。</p>
     *
     * @param userId         当前用户 ID，用于权限校验
     * @param conversationId 待删除会话 ID
     * @throws BizException 会话不存在、不归属当前用户或已删除时抛出
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
