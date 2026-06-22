import { request } from "./request";
import type {
  AgentObservationAlertRecordInfo,
  AgentObservationAlertRecordQuery,
  AgentObservationAlertRuleInfo,
  AgentObservationAlertRuleQuery,
  AgentObservationDashboard,
  AgentObservationDashboardQuery,
  AgentObservationEventInfo,
  AgentObservationEventQuery,
  AgentObservationStatItem,
  AgentTraceRetentionPolicyInfo,
  AgentTraceRetentionPreview,
  PageResult
} from "./types";

/**
 * 查询 Agent 观测看板。
 *
 * @param query 查询条件
 */
export function getObservationDashboard(query: AgentObservationDashboardQuery) {
  return request<AgentObservationDashboard>(`/admin/agent/observations/dashboard?${toQueryString(query)}`);
}

/**
 * 查询失败分类统计。
 *
 * @param query 查询条件
 */
export function getObservationFailureStats(query: AgentObservationDashboardQuery) {
  return request<AgentObservationStatItem[]>(`/admin/agent/observations/failure-stats?${toQueryString(query)}`);
}

/**
 * 分页查询统一观测事件。
 *
 * @param query 查询条件
 */
export function pageObservationEvents(query: AgentObservationEventQuery) {
  return request<PageResult<AgentObservationEventInfo>>(`/admin/agent/observations/page?${toQueryString(query)}`);
}

/**
 * 查询统一观测事件详情。
 *
 * @param id 事件 ID
 */
export function getObservationEventDetail(id: number) {
  return request<AgentObservationEventInfo>(`/admin/agent/observations/${id}`);
}

/**
 * 分页查询告警规则。
 *
 * @param query 查询条件
 */
export function pageObservationAlertRules(query: AgentObservationAlertRuleQuery) {
  return request<PageResult<AgentObservationAlertRuleInfo>>(
    `/admin/agent/observations/alert-rules/page?${toQueryString(query)}`
  );
}

/**
 * 新增告警规则。
 *
 * @param payload 告警规则表单
 */
export function createObservationAlertRule(payload: AgentObservationAlertRuleInfo) {
  return request<AgentObservationAlertRuleInfo>("/admin/agent/observations/alert-rules", {
    method: "POST",
    body: JSON.stringify(payload)
  });
}

/**
 * 修改告警规则。
 *
 * @param id 规则 ID
 * @param payload 告警规则表单
 */
export function updateObservationAlertRule(id: number, payload: AgentObservationAlertRuleInfo) {
  return request<AgentObservationAlertRuleInfo>(`/admin/agent/observations/alert-rules/${id}`, {
    method: "PUT",
    body: JSON.stringify(payload)
  });
}

/**
 * 删除告警规则。
 *
 * @param id 规则 ID
 */
export function deleteObservationAlertRule(id: number) {
  return request<void>(`/admin/agent/observations/alert-rules/${id}`, {
    method: "DELETE"
  });
}

/**
 * 手动评估所有启用告警规则。
 */
export function evaluateObservationAlertRules() {
  return request<AgentObservationAlertRecordInfo[]>("/admin/agent/observations/alert-rules/evaluate", {
    method: "POST"
  });
}

/**
 * 分页查询告警记录。
 *
 * @param query 查询条件
 */
export function pageObservationAlertRecords(query: AgentObservationAlertRecordQuery) {
  return request<PageResult<AgentObservationAlertRecordInfo>>(
    `/admin/agent/observations/alert-records/page?${toQueryString(query)}`
  );
}

/**
 * 更新告警记录状态。
 *
 * @param id 告警记录 ID
 * @param status OPEN/RESOLVED/IGNORED
 */
export function updateObservationAlertRecordStatus(id: number, status: string) {
  return request<AgentObservationAlertRecordInfo>(
    `/admin/agent/observations/alert-records/${id}/status?${toQueryString({ status })}`,
    { method: "PUT" }
  );
}

/**
 * 查询 Trace 保留策略列表。
 */
export function listTraceRetentionPolicies() {
  return request<AgentTraceRetentionPolicyInfo[]>("/admin/agent/observations/retention-policies");
}

/**
 * 新增 Trace 保留策略。
 *
 * @param payload 保留策略表单
 */
export function createTraceRetentionPolicy(payload: AgentTraceRetentionPolicyInfo) {
  return request<AgentTraceRetentionPolicyInfo>("/admin/agent/observations/retention-policies", {
    method: "POST",
    body: JSON.stringify(payload)
  });
}

/**
 * 修改 Trace 保留策略。
 *
 * @param id 策略 ID
 * @param payload 保留策略表单
 */
export function updateTraceRetentionPolicy(id: number, payload: AgentTraceRetentionPolicyInfo) {
  return request<AgentTraceRetentionPolicyInfo>(`/admin/agent/observations/retention-policies/${id}`, {
    method: "PUT",
    body: JSON.stringify(payload)
  });
}

/**
 * 预览 Trace 保留策略命中数据量。
 *
 * @param id 策略 ID
 */
export function previewTraceRetentionPolicy(id: number) {
  return request<AgentTraceRetentionPreview>(`/admin/agent/observations/retention-policies/${id}/preview`);
}

/**
 * 手动执行 Trace 保留策略。
 *
 * @param id 策略 ID
 */
export function executeTraceRetentionPolicy(id: number) {
  return request<AgentTraceRetentionPreview>(`/admin/agent/observations/retention-policies/${id}/execute`, {
    method: "POST"
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
