import { request } from "./request";
import type {
  AgentFollowUpApplicationInfo,
  AgentFollowUpApplicationQuery,
  AgentFollowUpRuleInfo,
  AgentFollowUpRuleQuery,
  PageResult
} from "./types";

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
 * 分页查询求职跟进 Agent 明细。
 */
export function pageFollowUpApplications(query: AgentFollowUpApplicationQuery) {
  return request<PageResult<AgentFollowUpApplicationInfo>>(
    `/admin/agent/follow-up/applications/page?${toQueryString(query)}`
  );
}

/**
 * 分页查询自动跟进规则。
 */
export function pageFollowUpRules(query: AgentFollowUpRuleQuery) {
  return request<PageResult<AgentFollowUpRuleInfo>>(`/admin/agent/follow-up/rules/page?${toQueryString(query)}`);
}

/**
 * 新增自动跟进规则。
 */
export function createFollowUpRule(payload: AgentFollowUpRuleInfo) {
  return request<AgentFollowUpRuleInfo>("/admin/agent/follow-up/rules", {
    method: "POST",
    body: JSON.stringify(payload)
  });
}

/**
 * 修改自动跟进规则。
 */
export function updateFollowUpRule(id: number, payload: AgentFollowUpRuleInfo) {
  return request<AgentFollowUpRuleInfo>(`/admin/agent/follow-up/rules/${id}`, {
    method: "PUT",
    body: JSON.stringify(payload)
  });
}

/**
 * 删除自动跟进规则。
 */
export function deleteFollowUpRule(id: number) {
  return request<void>(`/admin/agent/follow-up/rules/${id}`, { method: "DELETE" });
}

/**
 * 手动触发规则扫描，方便联调规则是否能创建提醒。
 */
export function scanFollowUpRules() {
  return request<number>("/admin/agent/follow-up/rules/scan", { method: "POST" });
}
