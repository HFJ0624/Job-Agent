package com.job.bootstrap.service.impl;

import com.job.agent.JobAgentAssistant;
import com.job.bootstrap.agent.context.AgentRuntimeContext;
import com.job.bootstrap.agent.intent.AgentIntentCode;
import com.job.bootstrap.agent.intent.AgentIntentRouter;
import com.job.bootstrap.mapper.AiConversationMapper;
import com.job.bootstrap.mapper.AiMessageMapper;
import com.job.bootstrap.service.AgentChatService;
import com.job.bootstrap.service.AgentTraceService;
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
 * 职责：
 * 1. 获取或创建 AI 会话。
 * 2. 保存用户消息。
 * 3. 设置 AgentRuntimeContext。
 * 4. 调用 LangChain4j Agent。
 * 5. 保存助手回复。
 * 6. 保存主链路 Trace。
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

    /**
     * LangChain4j AI Service 代理对象。
     * 后续会在配置类中注册工具，让模型可以调用 Java 方法。
     */
    private final JobAgentAssistant jobAgentAssistant;

    /**
     * Agent Trace 统一记录服务。
     */
    private final AgentTraceService agentTraceService;

    /**
     * 意图路由器。
     * 第一版使用规则识别，后续可以升级为大模型分类。
     */
    private final AgentIntentRouter agentIntentRouter;

    /**
     * 执行一次 AI 对话。
     *
     * @param userId 当前登录用户ID
     * @param conversationId 会话ID，可以为空
     * @param message 用户输入
     * @return Agent 回复
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public AgentChatVO chat(Long userId, Long conversationId, String message) {
        long start = System.currentTimeMillis();

        /*
         * 1. 每次用户发起一次对话，生成一个 traceId。
         *    这个 traceId 会贯穿:
         *    - 主对话日志
         *    - 工具调用日志
         *    - 异常日志
         */
        String traceId = UUID.randomUUID().toString().replace("-", "");

        /*
         * 2. 识别用户意图。
         *    当前只是规则识别，作用是:
         *    - Trace 日志可以分类
         *    - 后续可以根据意图走不同 Agent 编排流程
         */
        AgentIntentCode intentCode = agentIntentRouter.route(message);

        /*
         * 3. 获取或创建会话。
         *    conversationId 为空时自动创建新会话。
         */
        AiConversation conversation = getOrCreateConversation(userId, conversationId, message);

        /*
         * 4. 保存用户消息。
         */
        saveMessage(conversation.getId(), userId, ROLE_USER, message, null);

        try {
            /*
             * 5. 设置 Agent 运行时上下文。
             *    重点:
             *    - userId 不让大模型传
             *    - conversationId 不让大模型传
             *    - traceId 不让大模型传
             *    - 工具内部通过 AgentRuntimeContext 获取
             */
            AgentRuntimeContext.set(
                    userId,
                    conversation.getId(),
                    traceId,
                    intentCode.name()
            );

            /*
             * 6. 调用 Agent。
             *    conversation.getId() 作为 memoryId，用于绑定多轮对话上下文。
             */
            String answer = jobAgentAssistant.chat(conversation.getId(), message);

            /*
             * 7. 保存助手消息。
             */
            saveMessage(conversation.getId(), userId, ROLE_ASSISTANT, answer, null);

            /*
             * 8. 更新会话时间。
             *    这样前端会话列表可以按最近聊天排序。
             */
            touchConversation(conversation);

            /*
             * 9. 保存主对话 Trace。
             *    工具调用 Trace 会在 Tool 内部单独保存。
             */
            agentTraceService.saveTrace(
                    traceId,
                    userId,
                    conversation.getId(),
                    intentCode.name(),
                    null,
                    Map.of("message", message),
                    Map.of("answer", answer),
                    "SUCCESS",
                    null,
                    System.currentTimeMillis() - start
            );

            //返回结果
            AgentChatVO vo = new AgentChatVO();
            vo.setConversationId(conversation.getId());
            vo.setAnswer(answer);
            return vo;

        } catch (Exception e) {
            /*
             * 10. 异常也必须落 Trace。
             *     企业级 Agent 项目中，失败链路比成功链路更重要。
             */
            agentTraceService.saveTrace(
                    traceId,
                    userId,
                    conversation.getId(),
                    intentCode.name(),
                    null,
                    Map.of("message", message),
                    null,
                    "FAILED",
                    e.getMessage(),
                    System.currentTimeMillis() - start
            );

            throw e;

        } finally {
            /*
             * 11. 清理 ThreadLocal。
             *     这是必须做的，否则线程池复用时可能串用户。
             */
            AgentRuntimeContext.clear();
        }
    }

    /**
     * 获取或创建会话。
     */
    private AiConversation getOrCreateConversation(Long userId, Long conversationId, String firstMessage) {
        if (conversationId != null) {
            AiConversation exist = aiConversationMapper.selectById(conversationId);

            /*
             * 只允许用户访问自己的会话。
             * 这一步是用户数据隔离，防止越权访问。
             */
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
     * 保存一条聊天消息。
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
     * 更新会话更新时间。
     */
    private void touchConversation(AiConversation conversation) {
        conversation.setUpdateTime(new java.util.Date());
        aiConversationMapper.updateById(conversation);
    }
}