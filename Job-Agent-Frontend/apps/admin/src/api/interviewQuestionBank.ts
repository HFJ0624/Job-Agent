import { request } from "./request";
import type {
  InterviewQuestionBankInfo,
  InterviewQuestionBankQuery,
  InterviewQuestionImportPayload,
  InterviewQuestionImportResult,
  PageResult,
  RagIndexResult
} from "./types";

export function importLocalInterviewQuestions(payload: InterviewQuestionImportPayload) {
  return request<InterviewQuestionImportResult>("/admin/interview/question-bank/import-local", {
    method: "POST",
    body: JSON.stringify(payload)
  });
}

export function pageInterviewQuestionBank(query: InterviewQuestionBankQuery) {
  return request<PageResult<InterviewQuestionBankInfo>>(`/admin/interview/question-bank/page?${toQueryString(query)}`);
}

export function getInterviewQuestionBankDetail(id: number) {
  return request<InterviewQuestionBankInfo>(`/admin/interview/question-bank/${id}`);
}

export function updateInterviewQuestionStatus(id: number, status: string) {
  const params = new URLSearchParams();
  params.set("status", status);
  return request<void>(`/admin/interview/question-bank/${id}/status?${params.toString()}`, {
    method: "PUT"
  });
}

export function indexInterviewQuestion(id: number) {
  return request<RagIndexResult>(`/admin/interview/question-bank/${id}/index`, {
    method: "POST"
  });
}

export function indexAllInterviewQuestions() {
  return request<RagIndexResult>("/admin/interview/question-bank/index-all", {
    method: "POST"
  });
}

function toQueryString(query: object) {
  const params = new URLSearchParams();
  Object.entries(query as Record<string, unknown>).forEach(([key, value]) => {
    if (value === undefined || value === null || value === "") {
      return;
    }
    params.set(key, String(value));
  });
  return params.toString();
}
