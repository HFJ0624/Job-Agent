package com.job.bootstrap.service;

import com.job.bootstrap.ai.TokenConsumer;
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

    /**
     * 和 AI 助手对话，并显式控制是否恢复上次失败的计划。
     *
     * 方法步骤:
     * 1. retry=false 时保持原有语义：已失败的计划直接返回历史失败结果，不重复执行。
     * 2. retry=true 且传回 planId 时，Executor 会从第一个未完成步骤继续执行，
     *    已完成的步骤采用历史结果，副作用工具不会重复执行。
     * 3. 恢复执行同样经过用户确认校验，confirmedToolNames 需要原样传回。
     *
     * @param userId 当前用户ID
     * @param conversationId 会话ID，可以为空
     * @param planId 已存在的计划ID，可以为空
     * @param message 用户消息
     * @param confirmedToolNames 本轮用户已确认允许执行的工具名
     * @param retry 是否恢复上次失败的计划
     * @return 助手回复
     */
    AgentChatVO chat(
            Long userId,
            Long conversationId,
            Long planId,
            String message,
            List<String> confirmedToolNames,
            Boolean retry
    );

    /**
     * 流式对话（任务 G5）：主链路与 chat 一致，总结 token 实时回调给 streamSink。
     * 落库/记忆/Trace/护栏行为与 chat 完全一致；streamSink 为空时等价于 chat。
     *
     * @param streamSink token 消费者（可为 null）
     * @return Agent 回复（流式结束后返回完整结果，供 SSE done 事件使用）
     */
    AgentChatVO streamChat(
            Long userId,
            Long conversationId,
            Long planId,
            String message,
            List<String> confirmedToolNames,
            Boolean retry,
            TokenConsumer streamSink
    );
}
