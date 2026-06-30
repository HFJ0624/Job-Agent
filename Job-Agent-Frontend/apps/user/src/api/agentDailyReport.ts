import { request } from "./request";
import type { AgentDailyReportInfo, AgentDailyReportSubscriptionInfo } from "./types";

/**
 * 查询最近 Agent 日报。
 */
export function listRecentAgentDailyReports(limit = 7) {
  return request<AgentDailyReportInfo[]>(`/front/agent-daily-reports/recent?limit=${limit}`);
}

/**
 * 手动生成今日日报。
 */
export function generateTodayAgentDailyReport(sendEmail = true) {
  return request<AgentDailyReportInfo>(
    `/front/agent-daily-reports/today/generate?sendEmail=${sendEmail}`,
    {
      method: "POST"
    }
  );
}

/**
 * 查询我的 Agent 日报订阅配置。
 */
export function getAgentDailyReportSubscription() {
  return request<AgentDailyReportSubscriptionInfo>("/front/agent-daily-reports/subscription");
}

/**
 * 保存我的 Agent 日报订阅配置。
 */
export function saveAgentDailyReportSubscription(payload: {
  enabled: number;
  sendTime: string;
  emailEnabled: number;
}) {
  return request<AgentDailyReportSubscriptionInfo>("/front/agent-daily-reports/subscription", {
    method: "POST",
    body: JSON.stringify(payload)
  });
}
