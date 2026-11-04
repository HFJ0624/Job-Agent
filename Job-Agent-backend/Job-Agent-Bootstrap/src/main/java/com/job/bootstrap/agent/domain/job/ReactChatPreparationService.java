package com.job.bootstrap.agent.domain.job;

import com.job.bootstrap.mapper.AiConversationMapper;
import com.job.bootstrap.mapper.AiMessageMapper;
import com.job.common.entity.agent.AiConversation;
import com.job.common.entity.agent.AiMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.util.Date;

/**
 * 功能: ReAct 分支的会话准备（第一阶段验收修复 R6）。
 *
 * 解决什么问题:
 * 1. 旧链路把“会话创建 + 用户消息 + 模型调用 + 计划执行”全包在一个事务里，
 *    模型等待期间长事务一直悬挂；ReAct 分支如果在旧链路内部执行，同样逃不开这个长事务；
 * 2. 这里把**会话与用户消息**放进短事务先提交，随后 ReAct 执行（模型/工具）完全在事务外，
 *    助手消息再走一个短事务——不再靠 NOT_SUPPORTED 悬挂外层未提交事务。
 */
@Slf4j
@Service
public class ReactChatPreparationService {

    private static final String ROLE_USER = "USER";
    private static final String ROLE_ASSISTANT = "ASSISTANT";
    private static final String CONVERSATION_TYPE = "JOB_AGENT";
    private static final int NOT_DELETED = 0;

    private final AiConversationMapper aiConversationMapper;
    private final AiMessageMapper aiMessageMapper;
    private final TransactionTemplate transactionTemplate;

    public ReactChatPreparationService(AiConversationMapper aiConversationMapper,
                                       AiMessageMapper aiMessageMapper,
                                       PlatformTransactionManager transactionManager) {
        this.aiConversationMapper = aiConversationMapper;
        this.aiMessageMapper = aiMessageMapper;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /**
     * 短事务：获取/创建会话并保存用户消息。
     *
     * @return 会话 ID
     */
    public Long prepareConversation(Long userId, Long conversationId, String message) {
        return transactionTemplate.execute(status -> {
            AiConversation conversation = loadOrCreate(userId, conversationId, message);
            saveMessage(conversation.getId(), userId, ROLE_USER, message);
            conversation.setUpdateTime(new Date());
            aiConversationMapper.updateById(conversation);
            return conversation.getId();
        });
    }

    /**
     * 短事务：保存助手消息并刷新会话时间。
     */
    public void saveAssistantMessage(Long conversationId, Long userId, String answer) {
        if (conversationId == null || !StringUtils.hasText(answer)) {
            return;
        }
        transactionTemplate.executeWithoutResult(status -> {
            saveMessage(conversationId, userId, ROLE_ASSISTANT, answer);
            AiConversation conversation = aiConversationMapper.selectById(conversationId);
            if (conversation != null) {
                conversation.setUpdateTime(new Date());
                aiConversationMapper.updateById(conversation);
            }
        });
    }

    private AiConversation loadOrCreate(Long userId, Long conversationId, String title) {
        if (conversationId != null) {
            AiConversation existing = aiConversationMapper.selectById(conversationId);
            if (existing != null && userId.equals(existing.getUserId())) {
                return existing;
            }
        }
        AiConversation conversation = new AiConversation();
        conversation.setUserId(userId);
        conversation.setConversationType(CONVERSATION_TYPE);
        conversation.setTitle(StringUtils.hasText(title) && title.length() > 30 ? title.substring(0, 30) : title);
        conversation.setIsDeleted(NOT_DELETED);
        conversation.setCreateTime(new Date());
        conversation.setUpdateTime(new Date());
        aiConversationMapper.insert(conversation);
        return conversation;
    }

    private void saveMessage(Long conversationId, Long userId, String role, String content) {
        AiMessage message = new AiMessage();
        message.setConversationId(conversationId);
        message.setUserId(userId);
        message.setRole(role);
        message.setContent(content);
        message.setIsDeleted(NOT_DELETED);
        message.setCreateTime(new Date());
        aiMessageMapper.insert(message);
    }
}
