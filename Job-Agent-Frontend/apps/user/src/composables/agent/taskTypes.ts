/**
 * 功能: Agent 任务卡片的共享类型（页面与 composable 共用，避免各写一份）。
 */

export type TaskNodeStatus = "PENDING" | "RUNNING" | "SUCCEEDED" | "FAILED" | "SKIPPED";

export interface TaskNodeView {
  key: string;
  label: string;
  status: TaskNodeStatus;
  summary: string;
}

export interface TaskCard {
  planId: number | null;
  runId: number | null;
  /** 规范化后的状态：RUNNING / WAITING_INPUT / SUCCEEDED / FAILED / CANCELLED */
  status: string;
  nodes: TaskNodeView[];
  summaries: string[];
  pendingQuestion: string;
  failReason: string;
}

