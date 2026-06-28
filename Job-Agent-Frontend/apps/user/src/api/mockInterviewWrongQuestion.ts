import { request } from "./request";
import type { MockInterviewWrongQuestionInfo, PageResult } from "./types";

/**
 * 分页查询当前用户模拟面试错题本。
 */
export function pageMockInterviewWrongQuestions(params: {
  pageNum: number;
  pageSize: number;
  masteryStatus?: string;
  keyword?: string;
}) {
  const query = new URLSearchParams();

  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== "") {
      query.append(key, String(value));
    }
  });

  return request<PageResult<MockInterviewWrongQuestionInfo>>(
    `/front/mock-interview-wrong-questions/page?${query.toString()}`
  );
}

/**
 * 修改错题掌握状态。
 */
export function updateMockInterviewWrongQuestionStatus(id: number | string, masteryStatus: string) {
  return request<MockInterviewWrongQuestionInfo>(
    `/front/mock-interview-wrong-questions/${id}/mastery-status`,
    {
      method: "PUT",
      body: JSON.stringify({ masteryStatus })
    }
  );
}
