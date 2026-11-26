package com.job.bootstrap.ai.turn;

import java.util.List;

/**
 * 功能: 结构化模型轮次响应（第一阶段）。
 *
 * 说明:
 * 1. content 可为空——只要 toolCalls 非空就是合法响应（模型选择调用工具）；
 * 2. finishReason 用于区分 stop / tool_calls / length（输出截断）/ content_filter；
 * 3. usage 记录真实 token 用量，缺失时由网关按估算值补齐（标注 ESTIMATED）。
 *
 * @param content      文本内容（可空）
 * @param toolCalls    工具调用列表（可空）
 * @param finishReason 结束原因（stop/tool_calls/length/content_filter/其他）
 * @param inputTokens  输入 token
 * @param outputTokens 输出 token
 */
public record AiChatTurnResponse(
        String content,
        List<AiToolCall> toolCalls,
        String finishReason,
        Integer inputTokens,
        Integer outputTokens
) {

    public boolean hasToolCalls() {
        return toolCalls != null && !toolCalls.isEmpty();
    }

    public boolean truncated() {
        return "length".equalsIgnoreCase(finishReason);
    }
}
