import { request } from "./request";
import type { AgentOperationDashboard } from "./types";

/**
 * 查询 Agent 运营看板。
 *
 * 第一版不额外传筛选条件，后端默认统计最近 7 天的日报与行动项数据。
 */
export function getAgentOperationDashboard() {
  return request<AgentOperationDashboard>("/admin/agent/operations/dashboard");
}
