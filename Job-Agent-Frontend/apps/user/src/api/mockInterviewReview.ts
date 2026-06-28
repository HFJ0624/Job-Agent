import { request } from "./request";
import type { MockInterviewReviewInfo, MockInterviewStudyPlanInfo } from "./types";

/**
 * 生成模拟面试复盘报告。
 *
 * @param sessionId 模拟面试会话ID
 */
export function generateMockInterviewReview(sessionId: number | string) {
    return request<MockInterviewReviewInfo>("/front/mock-interview-reviews/generate", {
        method: "POST",
        body: JSON.stringify({
            sessionId
        })
    });
}

/**
 * 查询某轮模拟面试最近一次复盘报告。
 *
 * @param sessionId 模拟面试会话ID
 */
export function getLatestMockInterviewReview(sessionId: number | string) {
    return request<MockInterviewReviewInfo | null>(
        `/front/mock-interview-reviews/latest?sessionId=${sessionId}`
    );
}

/**
 * 查询模拟面试复盘后的补课清单。
 *
 * @param sessionId 模拟面试会话ID
 */
export function getMockInterviewStudyPlan(sessionId: number | string) {
    return request<MockInterviewStudyPlanInfo>(
        `/front/mock-interview-reviews/study-plan?sessionId=${sessionId}`
    );
}
