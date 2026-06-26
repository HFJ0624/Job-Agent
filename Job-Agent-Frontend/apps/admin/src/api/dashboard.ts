import { request } from "./request";
import type { AdminDashboardOverview } from "./types";

/**
 * 查询后台首页真实数据库看板数据。
 */
export function getAdminDashboardOverview() {
  return request<AdminDashboardOverview>("/admin/dashboard/overview");
}
