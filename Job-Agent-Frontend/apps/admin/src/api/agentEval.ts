import { request } from "./request";
import type {
  AgentEvalCaseInfo,
  AgentEvalCaseQuery,
  AgentEvalDatasetInfo,
  AgentEvalDatasetQuery,
  AgentEvalResultInfo,
  AgentEvalResultQuery,
  AgentEvalRunInfo,
  AgentEvalRunQuery,
  PageResult
} from "./types";

function toSearchParams(query: object) {
  const params = new URLSearchParams();
  Object.entries(query as Record<string, unknown>).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== "") {
      params.append(key, String(value));
    }
  });
  return params.toString();
}

export function pageEvalDatasets(query: AgentEvalDatasetQuery) {
  return request<PageResult<AgentEvalDatasetInfo>>(`/admin/agent/eval/datasets/page?${toSearchParams(query)}`);
}

export function listEnabledEvalDatasets() {
  return request<AgentEvalDatasetInfo[]>("/admin/agent/eval/datasets/enabled");
}

export function saveEvalDataset(payload: AgentEvalDatasetInfo) {
  const id = payload.id;
  return request<AgentEvalDatasetInfo>(id ? `/admin/agent/eval/datasets/${id}` : "/admin/agent/eval/datasets", {
    method: id ? "PUT" : "POST",
    body: JSON.stringify(payload)
  });
}

export function deleteEvalDataset(id: number) {
  return request<void>(`/admin/agent/eval/datasets/${id}`, { method: "DELETE" });
}

export function pageEvalCases(query: AgentEvalCaseQuery) {
  return request<PageResult<AgentEvalCaseInfo>>(`/admin/agent/eval/cases/page?${toSearchParams(query)}`);
}

export function saveEvalCase(payload: AgentEvalCaseInfo) {
  const id = payload.id;
  return request<AgentEvalCaseInfo>(id ? `/admin/agent/eval/cases/${id}` : "/admin/agent/eval/cases", {
    method: id ? "PUT" : "POST",
    body: JSON.stringify(payload)
  });
}

export function deleteEvalCase(id: number) {
  return request<void>(`/admin/agent/eval/cases/${id}`, { method: "DELETE" });
}

export function runEvalCase(id: number) {
  return request<AgentEvalRunInfo>(`/admin/agent/eval/run/${id}`, { method: "POST" });
}

export function runEvalDataset(id: number) {
  return request<AgentEvalRunInfo>(`/admin/agent/eval/run-dataset/${id}`, { method: "POST" });
}

export function runAllEvalCases() {
  return request<AgentEvalRunInfo>("/admin/agent/eval/runs/all", { method: "POST" });
}

export function setEvalBaseline(runId: number) {
  return request<AgentEvalRunInfo>(`/admin/agent/eval/runs/${runId}/baseline`, { method: "POST" });
}

export function pageEvalRuns(query: AgentEvalRunQuery) {
  return request<PageResult<AgentEvalRunInfo>>(`/admin/agent/eval/runs/page?${toSearchParams(query)}`);
}

export function pageEvalResults(query: AgentEvalResultQuery) {
  return request<PageResult<AgentEvalResultInfo>>(`/admin/agent/eval/results/page?${toSearchParams(query)}`);
}
