import { request } from "./request";
import type { InterviewPrepareInfo } from "./types";

/**
 * 生成面试准备。
 */
export function generateInterviewPrepare(data: {
  applicationId: number | string;
  resumeId?: number | string;
}) {
  return request<InterviewPrepareInfo>("/front/interview-prepare/generate", {
    method: "POST",
    body: JSON.stringify(data)
  });
}

/**
 * 查询最近一次面试准备。
 */
export function getLatestInterviewPrepare(applicationId: number | string) {
  return request<InterviewPrepareInfo | null>(
    `/front/interview-prepare/latest?applicationId=${applicationId}`
  );
}