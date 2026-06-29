import { request } from "./request";
import type { MockInterviewLearningPlanInfo, MockInterviewStudyPlanRetestInfo } from "./types";

/**
 * 基于错题本生成 AI 面试学习计划。
 */
export function generateMockInterviewLearningPlan(planDays: number) {
  return request<MockInterviewLearningPlanInfo>("/front/mock-interview-learning-plans/generate", {
    method: "POST",
    body: JSON.stringify({ planDays })
  });
}

/**
 * 查询当前用户最新学习计划。
 */
export function getLatestMockInterviewLearningPlan() {
  return request<MockInterviewLearningPlanInfo | null>("/front/mock-interview-learning-plans/latest");
}

/**
 * 修改每日学习任务完成状态。
 */
export function updateMockInterviewLearningPlanItemStatus(itemId: number | string, completionStatus: string) {
  return request<MockInterviewLearningPlanInfo>(
    `/front/mock-interview-learning-plans/items/${itemId}/completion-status`,
    {
      method: "PUT",
      body: JSON.stringify({ completionStatus })
    }
  );
}

/**
 * 为某个学习任务发起复测。
 */
export function startMockInterviewLearningPlanRetest(itemId: number | string) {
  return request<MockInterviewStudyPlanRetestInfo>(
    `/front/mock-interview-learning-plans/items/${itemId}/retests/start`,
    {
      method: "POST"
    }
  );
}

/**
 * 提交复测答案。
 */
export function submitMockInterviewLearningPlanRetest(retestId: number | string, userAnswer: string) {
  return request<MockInterviewStudyPlanRetestInfo>(
    `/front/mock-interview-learning-plans/retests/${retestId}/submit`,
    {
      method: "POST",
      body: JSON.stringify({ userAnswer })
    }
  );
}
