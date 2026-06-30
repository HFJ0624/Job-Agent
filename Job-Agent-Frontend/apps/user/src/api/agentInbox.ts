import { request } from "./request";
import type { AgentInboxInfo } from "./types";

/**
 * 查询今日 Agent 待办。
 */
export function getTodayAgentInbox() {
  return request<AgentInboxInfo>("/front/agent-inbox/today");
}
