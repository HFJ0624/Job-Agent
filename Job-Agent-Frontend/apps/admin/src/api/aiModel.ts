import { request } from "./request";
import type {
  AiModelCallLogInfo,
  AiModelCallLogQuery,
  AiModelConfigInfo,
  AiModelConfigQuery,
  AiModelCostStats,
  AiModelRouteInfo,
  AiModelRouteQuery,
  PageResult
} from "./types";

/**
 * 分页查询模型配置。
 */
export function pageModelConfigs(query: AiModelConfigQuery) {
  return request<PageResult<AiModelConfigInfo>>(`/admin/ai/models/configs/page?${toQueryString(query)}`);
}

/**
 * 查询启用模型列表。
 */
export function listActiveModelConfigs() {
  return request<AiModelConfigInfo[]>("/admin/ai/models/configs/active");
}

/**
 * 新增模型配置。
 */
export function createModelConfig(payload: AiModelConfigInfo) {
  return request<AiModelConfigInfo>("/admin/ai/models/configs", {
    method: "POST",
    body: JSON.stringify(payload)
  });
}

/**
 * 修改模型配置。
 */
export function updateModelConfig(id: number, payload: AiModelConfigInfo) {
  return request<AiModelConfigInfo>(`/admin/ai/models/configs/${id}`, {
    method: "PUT",
    body: JSON.stringify(payload)
  });
}

/**
 * 删除模型配置。
 */
export function deleteModelConfig(id: number) {
  return request<void>(`/admin/ai/models/configs/${id}`, {
    method: "DELETE"
  });
}

/**
 * 分页查询模型路由。
 */
export function pageModelRoutes(query: AiModelRouteQuery) {
  return request<PageResult<AiModelRouteInfo>>(`/admin/ai/models/routes/page?${toQueryString(query)}`);
}

/**
 * 新增模型路由。
 */
export function createModelRoute(payload: AiModelRouteInfo) {
  return request<AiModelRouteInfo>("/admin/ai/models/routes", {
    method: "POST",
    body: JSON.stringify(payload)
  });
}

/**
 * 修改模型路由。
 */
export function updateModelRoute(id: number, payload: AiModelRouteInfo) {
  return request<AiModelRouteInfo>(`/admin/ai/models/routes/${id}`, {
    method: "PUT",
    body: JSON.stringify(payload)
  });
}

/**
 * 删除模型路由。
 */
export function deleteModelRoute(id: number) {
  return request<void>(`/admin/ai/models/routes/${id}`, {
    method: "DELETE"
  });
}

/**
 * 分页查询模型调用日志。
 */
export function pageModelCallLogs(query: AiModelCallLogQuery) {
  return request<PageResult<AiModelCallLogInfo>>(`/admin/ai/models/call-logs/page?${toQueryString(query)}`);
}

/**
 * 查询模型调用成本统计。
 */
export function getModelCostStats(query: AiModelCallLogQuery) {
  return request<AiModelCostStats>(`/admin/ai/models/call-logs/stats?${toQueryString(query)}`);
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
