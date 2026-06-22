import { request } from "./request";
import type { AgentChatInfo, AgentConversationInfo, AgentMessageInfo, AgentChatMessage } from "./types";

export interface AgentChatPayload {
    conversationId: number | null;
    message: string;
    planId?: number | null;
    confirmedToolNames?: string[];
}

/**
 * 调用 AI 求职助手。
 *
 * @param payload 对话请求参数
 */
export function chatWithAgent(payload: AgentChatPayload) {
    return request<AgentChatInfo>("/front/agent/chat", {
        method: "POST",
        body: JSON.stringify(payload)
    });
}

/**
 * 查询当前用户的 AI 会话列表。
 */
export function listAgentConversations() {
    return request<AgentConversationInfo[]>("/front/agent/conversations");
}

/**
 * 查询指定会话下的历史消息。
 *
 * @param conversationId 会话ID
 */
export function listAgentMessages(conversationId: number) {
    return request<AgentMessageInfo[]>(
        `/front/agent/conversations/${conversationId}/messages`
    );
}

/**
 * 删除指定会话。
 *
 * @param conversationId 会话ID
 */
export function deleteAgentConversation(conversationId: number) {
    return request<void>(`/front/agent/conversations/${conversationId}`, {
        method: "DELETE"
    });
}
