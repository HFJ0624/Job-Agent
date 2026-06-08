import { request } from "./request";
import type { AgentChatInfo } from "./types";

/**
 * 调用 AI 求职助手。
 *
 * @param conversationId 会话ID，第一次对话可以不传
 * @param message 用户消息
 */
export function chatWithAgent(conversationId: number | null, message: string) {
    return request<AgentChatInfo>("/front/agent/chat", {
        method: "POST",
        body: JSON.stringify({
            conversationId,
            message
        })
    });
}