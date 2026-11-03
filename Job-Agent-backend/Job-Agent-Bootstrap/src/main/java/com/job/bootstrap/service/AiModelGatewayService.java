package com.job.bootstrap.service;

import com.job.bootstrap.ai.TokenConsumer;
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

    /**
     * 按业务场景 + 档位流式调用模型（任务 G5）。
     * 复用路由/限流/熔断/调用日志；流式失败时网关内部降级为阻塞调用（整段一个 token）。
     *
     * <p>默认实现：阻塞调用后把整段文本作为一个 token 回调——保证既有实现（含测试中的 lambda）
     * 无需改动即可编译，也定义了“最保守的流式语义”。</p>
     *
     * @param consumer token 消费者（降级时只会收到一个 token）
     * @return 完整模型输出文本
     */
    /**
     * 结构化轮次调用（第一阶段 ReAct）：完整消息序列 + 工具清单。
     *
     * <p>默认实现直接抛错——**不允许静默退化为一次文本 chat**：ReAct 链路依赖工具调用，
     * 悄悄降级会让上层误以为“ReAct 成功”。只有真正支持工具协议的实现类才能重写本方法。</p>
     *
     * @param sceneCode 业务场景编码（复用路由/限流/熔断/调用日志）
     * @param tier      模型档位，为空按 STANDARD
     * @param request   消息序列与工具清单
     * @param userId    用户 ID
     * @param traceId   链路 ID
     * @return 结构化轮次响应
     */
    default com.job.bootstrap.ai.turn.AiChatTurnResponse chatTurn(
            String sceneCode,
            AiModelTier tier,
            com.job.bootstrap.ai.turn.AiChatTurnRequest request,
            Long userId,
            String traceId
    ) {
        throw new com.job.exception.BizException("当前模型网关不支持结构化轮次调用（chatTurn）");
    }

    default String chatStream(
            String sceneCode,
            AiModelTier tier,
            Map<String, Object> variables,
            String userMessage,
            Long userId,
            String traceId,
            TokenConsumer consumer
    ) {
        String answer = chat(sceneCode, tier, variables, userMessage, userId, traceId);
        consumer.onToken(answer);
        return answer;
    }
}
