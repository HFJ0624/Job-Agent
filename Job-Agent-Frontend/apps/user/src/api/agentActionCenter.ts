import { request } from "./request";
import type { AgentActionItemInfo } from "./types";

/**
 * 查询待确认行动项。
 */
export function listPendingAgentActions(limit = 50) {
  return request<AgentActionItemInfo[]>(`/front/agent-actions/pending?limit=${limit}`);
}

/**
 * 标记行动项完成。
 */
export function markAgentActionDone(actionId: number, note?: string) {
  return request<void>(`/front/agent-actions/${actionId}/done`, {
    method: "POST",
    body: JSON.stringify({ note })
  });
}

/**
 * 忽略行动项。
 */
export function ignoreAgentAction(actionId: number, note?: string) {
  return request<void>(`/front/agent-actions/${actionId}/ignore`, {
    method: "POST",
    body: JSON.stringify({ note })
  });
}

/**
 * 稍后处理行动项。
 */
export function snoozeAgentAction(actionId: number, snoozeUntil: string, note?: string) {
  return request<void>(`/front/agent-actions/${actionId}/snooze`, {
    method: "POST",
    body: JSON.stringify({
      snoozeUntil,
      note
    })
  });
}
