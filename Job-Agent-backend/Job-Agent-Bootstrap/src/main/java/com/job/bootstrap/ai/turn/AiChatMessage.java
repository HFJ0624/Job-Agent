package com.job.bootstrap.ai.turn;

import java.util.List;

/**
 * 功能: 对话消息（第一阶段：结构化模型协议）。
 *
 * 设计说明:
 * 1. ReAct 链路需要完整的消息序列（system/user/assistant/tool），
 *    而不是原来 chat() 的“systemPrompt + userMessage”两段式；
 * 2. assistant 消息可以同时带 content 和 toolCalls（content 为空但 tool_calls 有值是合法响应）；
 * 3. tool 消息通过 toolCallId 与 assistant 的工具调用一一配对——协议完整性由 Runner 保证。
 *
 * @param role       system / user / assistant / tool
 * @param content    文本内容（tool 消息为工具结果 JSON；assistant 可为空）
 * @param toolCallId tool 消息对应哪个工具调用（assistant 消息为 null）
 * @param toolCalls  assistant 消息携带的工具调用列表（其他角色为 null）
 */
public record AiChatMessage(
        String role,
        String content,
        String toolCallId,
        List<AiToolCall> toolCalls
) {

    public static AiChatMessage system(String content) {
        return new AiChatMessage("system", content, null, null);
    }

    public static AiChatMessage user(String content) {
        return new AiChatMessage("user", content, null, null);
    }

    public static AiChatMessage assistant(String content, List<AiToolCall> toolCalls) {
        return new AiChatMessage("assistant", content, null, toolCalls);
    }

    public static AiChatMessage tool(String toolCallId, String content) {
        return new AiChatMessage("tool", content, toolCallId, null);
    }
}
