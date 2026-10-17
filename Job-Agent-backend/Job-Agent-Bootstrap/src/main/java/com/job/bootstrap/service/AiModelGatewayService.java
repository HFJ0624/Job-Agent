package com.job.bootstrap.service;

import com.job.enums.AiModelTier;

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
     * 按业务场景调用模型（不传档位，按 STANDARD 处理）。
     *
     * @param sceneCode 业务场景编码，例如 AGENT_SUMMARY
     * @param variables Prompt 变量
     * @param userMessage 用户消息
     * @param userId 用户 ID
     * @param traceId 链路 ID
     * @return 模型输出文本
     */
    String chat(String sceneCode, Map<String, Object> variables, String userMessage, Long userId, String traceId);

    /**
     * 按业务场景 + 模型档位调用模型（任务 G1 分级路由）。
     * 同一场景内可按任务难度选择不同成本/质量的模型；未配置对应档位路由时降级为场景默认路由。
     *
     * <p>默认实现委托给不传档位的重载，保证既有实现（含测试中的 lambda）无需改动即可编译。
     * 网关实现类会重写本方法以真正支持档位路由。</p>
     *
     * @param sceneCode 业务场景编码
     * @param tier 模型档位（LIGHT/STANDARD/PRO），为空按 STANDARD
     * @param variables Prompt 变量
     * @param userMessage 用户消息
     * @param userId 用户 ID
     * @param traceId 链路 ID
     * @return 模型输出文本
     */
    default String chat(String sceneCode, AiModelTier tier, Map<String, Object> variables, String userMessage, Long userId, String traceId) {
        return chat(sceneCode, variables, userMessage, userId, traceId);
    }
}
