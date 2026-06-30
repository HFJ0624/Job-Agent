import { request } from "./request";
import type { AgentInboxInfo } from "./types";

/**
 * 查询今日 Agent 待办。
 */
export function getTodayAgentInbox() {
  return request<AgentInboxInfo>("/front/agent-inbox/today");
}

/**
 * 标记 Agent 待办完成。
 */
export function markAgentInboxItemDone(
  itemKey: string,
  payload?: {
    note?: string;
    businessStatus?: string;
  }
) {
  return request<void>(`/front/agent-inbox/items/${encodeURIComponent(itemKey)}/done`, {
    method: "POST",
    body: JSON.stringify(payload || {})
  });
}

/**
 * 忽略 Agent 待办。
 */
export function ignoreAgentInboxItem(itemKey: string, note?: string) {
  return request<void>(`/front/agent-inbox/items/${encodeURIComponent(itemKey)}/ignore`, {
    method: "POST",
    body: JSON.stringify({ note })
  });
}

/**
 * 稍后提醒 Agent 待办。
 */
export function snoozeAgentInboxItem(itemKey: string, snoozeUntil: string, note?: string) {
  return request<void>(`/front/agent-inbox/items/${encodeURIComponent(itemKey)}/snooze`, {
    method: "POST",
    body: JSON.stringify({
      snoozeUntil,
      note
    })
  });
}
