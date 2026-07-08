package com.job.bootstrap.service;

import com.job.common.vo.agent.AgentChatVO;

import java.util.List;

/**
 * AI 助手聊天服务接口。
 *
 * <p>核心职责：承接用户与 AI 助手的多轮对话，完成意图识别、计划生成/执行、工具调用确认及最终回复组装。</p>
 *
 * <p>所属业务模块：AI 助手 - 对话（Chat）</p>
 *
 * <p>主要调用链：
 * AgentChatController -&gt; AgentChatService -&gt; AgentChatServiceImpl -&gt; AgentPlanningService / AgentPlanExecutorService / AgentMemoryCaptureService / AiModelGatewayService</p>
 */
public interface AgentChatService {

    /**
     * 和 AI 助手对话。
     *
     * @param userId 当前用户ID
     * @param conversationId 会话ID，可以为空
     * @param planId 已存在的计划ID，可以为空
     * @param message 用户消息
     * @param confirmedToolNames 本轮用户已确认允许执行的工具名
     * @return 助手回复
     */
    AgentChatVO chat(
            Long userId,
            Long conversationId,
            Long planId,
            String message,
            List<String> confirmedToolNames
    );
}
