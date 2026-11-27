package com.job.agent.application.chat;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.job.agent.persistence.mapper.AiConversationMapper;
import com.job.agent.persistence.mapper.AiMessageMapper;
import com.job.common.entity.agent.AiConversation;
import com.job.common.entity.agent.AiMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 功能: 会话与消息存储（从 AgentChatServiceImpl 抽出）。
 *
 * <p>职责：会话归属校验与创建、消息落库、会话更新时间维护。
 * 只做存储，不参与路由、Prompt、模型调用与结果渲染。</p>
 *
 * <p>事务：由调用方（聊天用例）决定事务边界，本类不单独开事务，
 * 保持原有"一次对话一个短事务"的语义。</p>
 */
@Service
@RequiredArgsConstructor
public class ConversationService {

    private static final int NOT_DELETED = 0;

    private final AiConversationMapper aiConversationMapper;
    private final AiMessageMapper aiMessageMapper;

    /** 取当前用户的会话；不存在或不属于该用户时创建新会话。 */
    public AiConversation getOrCreate(Long userId, Long conversationId, String firstMessage) {
        if (conversationId != null) {
            AiConversation exist = aiConversationMapper.selectById(conversationId);
            // 只允许访问自己的会话（用户数据隔离）
            if (exist != null && userId.equals(exist.getUserId())) {
                return exist;
            }
        }

        AiConversation conversation = new AiConversation();
        conversation.setUserId(userId);
        conversation.setConversationType("JOB_AGENT");
        conversation.setTitle(buildTitle(firstMessage));
        conversation.setIsDeleted(NOT_DELETED);
        aiConversationMapper.insert(conversation);
        return conversation;
    }

    /** 保存一条聊天消息。 */
    public void save(Long conversationId, Long userId, String role, String content, String toolName) {
        AiMessage message = new AiMessage();
        message.setConversationId(conversationId);
        message.setUserId(userId);
        message.setRole(role);
        message.setContent(content);
        message.setToolName(toolName);
        message.setTokenCount(0);
        message.setIsDeleted(NOT_DELETED);
        aiMessageMapper.insert(message);
    }

    /**
     * 回写最后一条助手消息内容（流式答复落库用）。
     *
     * <p>新链路先落"已校验答复"，流式改写成功后用本方法覆盖，保证刷新后看到的是同一份文本。</p>
     */
    public void updateLastAssistantContent(Long conversationId, String content) {
        if (conversationId == null || !StringUtils.hasText(content)) {
            return;
        }
        AiMessage last = aiMessageMapper.selectOne(new QueryWrapper<AiMessage>()
                .eq("conversation_id", conversationId)
                .eq("role", "ASSISTANT")
                .orderByDesc("id")
                .last("limit 1"));
        if (last == null) {
            return;
        }
        last.setContent(content);
        aiMessageMapper.updateById(last);
    }

    /** 更新会话更新时间（列表排序用）。 */
    public void touch(AiConversation conversation) {
        conversation.setUpdateTime(new java.util.Date());
        aiConversationMapper.updateById(conversation);
    }

    private String buildTitle(String message) {
        if (message == null || message.isBlank()) {
            return "新的求职对话";
        }
        String title = message.trim();
        return title.length() > 20 ? title.substring(0, 20) + "..." : title;
    }
}
