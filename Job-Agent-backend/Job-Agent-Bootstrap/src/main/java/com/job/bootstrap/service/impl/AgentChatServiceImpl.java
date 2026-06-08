package com.job.bootstrap.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.job.agent.JobAgentAssistant;
import com.job.bootstrap.agent.context.AgentUserContext;
import com.job.bootstrap.mapper.AgentTraceLogMapper;
import com.job.bootstrap.mapper.AiConversationMapper;
import com.job.bootstrap.mapper.AiMessageMapper;
import com.job.bootstrap.service.AgentChatService;
import com.job.common.entity.agent.AgentTraceLog;
import com.job.common.entity.agent.AiConversation;
import com.job.common.entity.agent.AiMessage;
import com.job.common.vo.agent.AgentChatVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

/**
 * 作者:hfj
 * 功能:AI 助手聊天服务实现
 * 日期: 2026/6/8 15:20
 */
@Service
@RequiredArgsConstructor
public class AgentChatServiceImpl implements AgentChatService {

    private static final int NOT_DELETED = 0;
    private static final String ROLE_USER = "USER";
    private static final String ROLE_ASSISTANT = "ASSISTANT";

    private final AiConversationMapper aiConversationMapper;
    private final AiMessageMapper aiMessageMapper;
    private final AgentTraceLogMapper agentTraceLogMapper;
    private final JobAgentAssistant jobAgentAssistant;
    private final ObjectMapper objectMapper;

    /**
     * 执行一次 AI 对话。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public AgentChatVO chat(Long userId, Long conversationId, String message) {
        long start = System.currentTimeMillis();
        String traceId = UUID.randomUUID().toString().replace("-", "");

        /*
         * 1. 如果前端没有传 conversationId，则自动创建一个新会话。
         */
        AiConversation conversation = getOrCreateConversation(userId, conversationId, message);

        /*
         * 2. 保存用户消息。
         */
        saveMessage(conversation.getId(), userId, ROLE_USER, message, null);

        try {
            //在调用 Agent 前，把当前登录用户ID放入 ThreadLocal。
            AgentUserContext.setUserId(userId);

            /*
             * 3. 调用 Agent。
             * 注意：conversationId 会作为 memoryId，让 LangChain4j 维护多轮上下文。
             */
            String answer = jobAgentAssistant.chat(conversation.getId(), message);

            /*
             * 4. 保存助手消息。
             */
            saveMessage(conversation.getId(), userId, ROLE_ASSISTANT, answer, null);

            /*
             * AI 回复后更新会话时间，用于前端会话列表排序。
             */
            touchConversation(conversation);

            /*
             * 5. 保存 Agent 调用日志。
             */
            saveTrace(
                    traceId,
                    userId,
                    conversation.getId(),
                    "AGENT_CHAT",
                    null,
                    Map.of("message", message),
                    Map.of("answer", answer),
                    "SUCCESS",
                    null,
                    System.currentTimeMillis() - start
            );

            AgentChatVO vo = new AgentChatVO();
            vo.setConversationId(conversation.getId());
            vo.setAnswer(answer);
            return vo;
        } catch (Exception e) {
            /*
             * 6. 异常也要记录，方便后台排查。
             */
            saveTrace(
                    traceId,
                    userId,
                    conversation.getId(),
                    "AGENT_CHAT",
                    null,
                    Map.of("message", message),
                    null,
                    "FAILED",
                    e.getMessage(),
                    System.currentTimeMillis() - start
            );

            throw e;
        }finally {
            //清理 ThreadLocal。
            AgentUserContext.clear();
        }
    }

    /**
     * 获取或创建会话。
     */
    private AiConversation getOrCreateConversation(Long userId, Long conversationId, String firstMessage) {
        if (conversationId != null) {
            AiConversation exist = aiConversationMapper.selectById(conversationId);

            if (exist != null && userId.equals(exist.getUserId())) {
                return exist;
            }
        }

        AiConversation conversation = new AiConversation();
        conversation.setUserId(userId);
        conversation.setConversationType("JOB_AGENT");
        conversation.setTitle(buildConversationTitle(firstMessage));
        conversation.setIsDeleted(NOT_DELETED);
        aiConversationMapper.insert(conversation);
        return conversation;
    }

    /**
     * 生成会话标题。
     */
    private String buildConversationTitle(String message) {
        if (message == null || message.isBlank()) {
            return "新的求职对话";
        }

        String title = message.trim();
        return title.length() > 20 ? title.substring(0, 20) + "..." : title;
    }

    /**
     * 保存一条消息。
     */
    private void saveMessage(Long conversationId, Long userId, String role, String content, String toolName) {
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
     * 保存 Agent Trace。
     */
    private void saveTrace(
            String traceId,
            Long userId,
            Long conversationId,
            String intentCode,
            String toolName,
            Object input,
            Object output,
            String status,
            String errorMsg,
            Long costTime
    ) {
        AgentTraceLog log = new AgentTraceLog();
        log.setTraceId(traceId);
        log.setUserId(userId);
        log.setConversationId(conversationId);
        log.setIntentCode(intentCode);
        log.setToolName(toolName);
        log.setInputData(toJson(input));
        log.setOutputData(toJson(output));
        log.setStatus(status);
        log.setErrorMsg(errorMsg);
        log.setCostTime(costTime);
        log.setIsDeleted(NOT_DELETED);
        agentTraceLogMapper.insert(log);
    }

    /**
     * 对象转 JSON。
     */
    private String toJson(Object value) {
        if (value == null) {
            return null;
        }

        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return "{}";
        }
    }

    /**
     * 更新会话的更新时间。
     * 说明:
     * 1. 用户继续对话后，左侧会话列表应该把该会话排到最前面。
     * 2. 所以每次 AI 回复完成后，更新一次 conversation.updateTime。
     */
    private void touchConversation(AiConversation conversation) {
        conversation.setUpdateTime(new java.util.Date());
        aiConversationMapper.updateById(conversation);
    }
}
