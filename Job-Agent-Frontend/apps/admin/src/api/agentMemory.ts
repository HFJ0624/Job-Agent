import { request } from "./request";
import type {
  AgentMemoryInfo,
  AgentMemoryQuery,
  AgentUserMemoryProfileInfo,
  PageResult
} from "./types";

/**
 * 分页查询 Agent 长期记忆。
 *
 * @param query 查询条件
 */
export function pageAgentMemories(query: AgentMemoryQuery) {
  return request<PageResult<AgentMemoryInfo>>(`/admin/agent/memories/page?${toQueryString(query)}`);
}

/**
 * 查询 Agent 长期记忆详情。
 *
 * @param id 记忆 ID
 */
export function getAgentMemoryDetail(id: number) {
  return request<AgentMemoryInfo>(`/admin/agent/memories/${id}`);
}

/**
 * 查询用户长期记忆画像。
 *
 * @param userId 用户 ID
 */
export function getAgentMemoryProfile(userId: string | number) {
  return request<AgentUserMemoryProfileInfo>(`/admin/agent/memories/profiles/${userId}`);
}

/**
 * 手动重建用户长期记忆画像。
 *
 * @param userId 用户 ID
 */
export function rebuildAgentMemoryProfile(userId: string | number) {
  return request<AgentUserMemoryProfileInfo>(`/admin/agent/memories/profiles/${userId}/rebuild`, {
    method: "POST"
  });
}

/**
 * 后台人工更新长期记忆状态。
 *
 * @param id 记忆 ID
 * @param status ACTIVE/ARCHIVED/INVALID
 */
export function updateAgentMemoryStatus(id: number, status: string) {
  return request<AgentMemoryInfo>(`/admin/agent/memories/${id}/status?${toQueryString({ status })}`, {
    method: "PUT"
  });
}

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
