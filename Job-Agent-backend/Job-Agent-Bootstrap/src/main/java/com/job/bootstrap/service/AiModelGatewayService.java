package com.job.bootstrap.service;

import java.util.Map;

/**
 * AI 模型统一调用网关接口。
 *
 * <p>核心职责：根据业务场景编码路由到对应的模型和 Prompt 模板，完成变量渲染、模型调用、结果返回及调用日志记录。</p>
 *
 * <p>所属业务模块：基础设施 - AI 模型网关</p>
 *
 * <p>主要调用链：
 * AgentChatService / AgentPlanningService / AgentMemoryCaptureService -&gt; AiModelGatewayService -&gt; AiModelGatewayServiceImpl -&gt; AiPromptRuntimeService / ModelProviderClient / AiModelCallLogRepository</p>
 */
public interface AiModelGatewayService {

    /**
     * 按业务场景调用模型。
     *
     * @param sceneCode 业务场景编码，例如 AGENT_SUMMARY
     * @param variables Prompt 变量
     * @param userMessage 用户消息
     * @param userId 用户 ID
     * @param traceId 链路 ID
     * @return 模型输出文本
     */
    String chat(String sceneCode, Map<String, Object> variables, String userMessage, Long userId, String traceId);
}
