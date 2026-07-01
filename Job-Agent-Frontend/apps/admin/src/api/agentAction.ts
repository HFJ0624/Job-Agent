import { request } from "./request";
import type { AgentActionItemInfo, AgentActionItemQuery, PageResult } from "./types";

function toQueryString(query: object) {
  const params = new URLSearchParams();

  Object.entries(query as Record<string, unknown>).forEach(([key, value]) => {
    if (value === undefined || value === null || value === "") {
      return;
    }
    params.set(key, String(value));
  });

  return params.toString();
}

/**
 * 分页查询 Agent 行动项。
 */
export function pageAgentActions(query: AgentActionItemQuery) {
  return request<PageResult<AgentActionItemInfo>>(`/admin/agent/actions/page?${toQueryString(query)}`);
}
