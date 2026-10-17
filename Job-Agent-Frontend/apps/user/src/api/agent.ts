import { request, getToken } from "./request";
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
 * 流式调用 AI 求职助手（任务 G5）。
 *
 * 说明:
 * 1. POST + fetch ReadableStream 手写 SSE 解析（EventSource 不支持 POST，这是流式 POST 的标准做法）；
 * 2. 每收到一个 token 事件回调 onToken，done 事件返回最终结果；
 * 3. 流式不可用（网络/浏览器/服务端降级失败）时抛错，由页面回退到阻塞接口。
 *
 * @param payload 对话请求参数
 * @param onToken 增量 token 回调
 */
export async function streamChatWithAgent(
    payload: AgentChatPayload,
    onToken: (token: string) => void
): Promise<AgentChatInfo> {
    const { tokenName, tokenValue } = getToken();
    const headers = new Headers({ "Content-Type": "application/json" });
    if (tokenValue) {
        headers.set(tokenName, tokenValue);
    }

    const response = await fetch("/front/agent/chat/stream", {
        method: "POST",
        headers,
        body: JSON.stringify(payload)
    });

    if (!response.ok || !response.body) {
        throw new Error(`流式请求失败：HTTP ${response.status}`);
    }

    const reader = response.body.getReader();
    const decoder = new TextDecoder();
    let buffer = "";
    let finalResult: AgentChatInfo | null = null;

    while (true) {
        const { done, value } = await reader.read();
        if (done) {
            break;
        }
        buffer += decoder.decode(value, { stream: true });

        // SSE 事件以空行分隔；最后一段可能不完整，留在缓冲区
        const blocks = buffer.split("\n\n");
        buffer = blocks.pop() ?? "";

        for (const block of blocks) {
            const event = parseSseEvent(block);
            if (!event) {
                continue;
            }
            if (event.name === "token" && typeof event.data === "string") {
                onToken(event.data);
            } else if (event.name === "done") {
                finalResult = event.data as AgentChatInfo;
            } else if (event.name === "error") {
                throw new Error(String(event.data));
            }
        }
    }

    if (!finalResult) {
        throw new Error("流式响应缺少 done 事件");
    }
    return finalResult;
}

/**
 * 解析单条 SSE 事件块：取 event: 名称与 data: 负载（JSON 优先，失败按纯文本）。
 */
function parseSseEvent(block: string): { name: string; data: unknown } | null {
    let name = "message";
    const dataLines: string[] = [];

    for (const line of block.split("\n")) {
        if (line.startsWith("event:")) {
            name = line.slice(6).trim();
        } else if (line.startsWith("data:")) {
            dataLines.push(line.slice(5).trim());
        }
    }

    if (!dataLines.length) {
        return null;
    }
    try {
        return { name, data: JSON.parse(dataLines.join("\n")) };
    } catch {
        return { name, data: dataLines.join("\n") };
    }
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
