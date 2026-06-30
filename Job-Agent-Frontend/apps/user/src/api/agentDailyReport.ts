import { request } from "./request";
import type { AgentDailyReportInfo } from "./types";

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
