import { request } from "./request";
import type { MockInterviewMediaRecordInfo, MockInterviewSessionInfo, MockInterviewSessionQuery, PageResult } from "./types";

function toQueryString(query: object) {
  const params = new URLSearchParams();
  Object.entries(query as Record<string, unknown>).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== "") {
      params.set(key, String(value));
    }
  });
  return params.toString();
}

/**
 * 分页查询模拟面试会话。
 */
export function pageMockInterviewSessions(query: MockInterviewSessionQuery) {
  return request<PageResult<MockInterviewSessionInfo>>(`/admin/mock-interviews/sessions/page?${toQueryString(query)}`);
}

/**
 * 查询模拟面试详情。
 */
export function getMockInterviewSessionDetail(sessionId: number | string) {
  return request<MockInterviewSessionInfo>(`/admin/mock-interviews/sessions/${sessionId}`);
}

/**
 * 查询模拟面试媒体记录。
 */
export function listMockInterviewMediaRecords(sessionId: number | string) {
  return request<MockInterviewMediaRecordInfo[]>(`/admin/mock-interviews/sessions/${sessionId}/media`);
}
