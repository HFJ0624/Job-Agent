import { request } from "./request";
import type { PageResult, WorkflowTaskInfo, WorkflowTaskLogInfo, WorkflowTaskQuery } from "./types";

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

/**
 * 分页查询工作流任务。
 */
export function pageWorkflowTasks(query: WorkflowTaskQuery) {
  return request<PageResult<WorkflowTaskInfo>>(`/admin/workflow/tasks/page?${toQueryString(query)}`);
}

/**
 * 查询任务阶段日志。
 */
export function listWorkflowTaskLogs(id: number) {
  return request<WorkflowTaskLogInfo[]>(`/admin/workflow/tasks/${id}/logs`);
}

/**
 * 手动重试任务。
 */
export function retryWorkflowTask(id: number) {
  return request<WorkflowTaskInfo>(`/admin/workflow/tasks/${id}/retry`, { method: "POST" });
}

/**
 * 手动取消任务。
 */
export function cancelWorkflowTask(id: number) {
  return request<WorkflowTaskInfo>(`/admin/workflow/tasks/${id}/cancel`, { method: "POST" });
}

/**
 * 创建 RAG 全量重建异步任务。
 */
export function createRagRebuildAllTask() {
  return request<WorkflowTaskInfo>("/admin/workflow/tasks/rag/rebuild-all", { method: "POST" });
}

/**
 * 创建指定用户 RAG 重建异步任务。
 */
export function createRagRebuildUserTask(userId: number) {
  return request<WorkflowTaskInfo>(`/admin/workflow/tasks/rag/rebuild-users/${userId}`, { method: "POST" });
}

/**
 * 创建 Eval 数据集异步回归任务。
 */
export function createEvalDatasetRunTask(datasetId: number) {
  return request<WorkflowTaskInfo>(`/admin/workflow/tasks/eval/datasets/${datasetId}/run`, { method: "POST" });
}
