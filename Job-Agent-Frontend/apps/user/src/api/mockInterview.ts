import { request } from "./request";
import type {
    MockInterviewAnswerInfo,
    MockInterviewQuestionInfo,
    MockInterviewSessionInfo
} from "./types";

/**
 * 开始模拟面试。
 */
export function startMockInterview(data: {
    applicationId: number | string;
    resumeId?: number | string;
    questionCount?: number;
}) {
    return request<MockInterviewSessionInfo>("/front/mock-interviews/start", {
        method: "POST",
        body: JSON.stringify(data)
    });
}

export function startAiInterview(data: {
    resumeId: number | string;
    jobId: number | string;
    questionCount?: number;
}) {
    return request<MockInterviewSessionInfo>("/front/mock-interviews/ai/start", {
        method: "POST",
        body: JSON.stringify(data)
    });
}

/**
 * 查询模拟面试详情。
 */
export function getMockInterviewDetail(sessionId: number | string) {
    return request<MockInterviewSessionInfo>(`/front/mock-interviews/${sessionId}`);
}

/**
 * 查询当前题目。
 */
export function getCurrentMockQuestion(sessionId: number | string) {
    return request<MockInterviewQuestionInfo | null>(
        `/front/mock-interviews/${sessionId}/current-question`
    );
}

/**
 * 提交回答。
 */
export function submitMockAnswer(
    sessionId: number | string,
    data: {
        questionId: number | string;
        answerContent: string;
    }
) {
    return request<MockInterviewAnswerInfo>(`/front/mock-interviews/${sessionId}/answer`, {
        method: "POST",
        body: JSON.stringify(data)
    });
}

export function submitMockAudioAnswer(
    sessionId: number | string,
    questionId: number | string,
    audio: Blob,
    durationSeconds?: number
) {
    const formData = new FormData();
    formData.append("questionId", String(questionId));
    formData.append("audio", audio, `answer-${questionId}.webm`);
    if (durationSeconds !== undefined) {
        formData.append("durationSeconds", String(durationSeconds));
    }

    return request<MockInterviewAnswerInfo>(`/front/mock-interviews/${sessionId}/answer/audio`, {
        method: "POST",
        body: formData
    });
}

/**
 * 结束模拟面试。
 */
export function finishMockInterview(sessionId: number | string) {
    return request<MockInterviewSessionInfo>(`/front/mock-interviews/${sessionId}/finish`, {
        method: "POST"
    });
}
