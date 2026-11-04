/**
 * 功能: Agent 任务卡片的静态显示映射（从 AgentPage.vue 抽出）。
 *
 * 只包含"状态码 → 中文/顺序"的静态表，不含响应式状态与请求逻辑；
 * 页面与任务卡片组件共用同一份，避免文案分叉。
 */

const NODE_LABELS: Record<string, string> = {
  RESUME_AGENT: "简历分析",
  JOB_SEARCH_AGENT: "岗位搜索",
  MATCH_AGENT: "匹配分析"
};

const PLAN_STATUS_MAP: Record<string, string> = {
  NEED_CLARIFICATION: "WAITING_INPUT",
  PLANNED: "RUNNING",
  COMPLETED: "SUCCEEDED",
  CANCELLED: "CANCELLED",
  FAILED: "FAILED"
};

const TASK_STATUS_LABELS: Record<string, string> = {
  RUNNING: "执行中",
  WAITING_INPUT: "等待补充",
  SUCCEEDED: "已完成",
  FAILED: "失败",
  CANCELLED: "已取消"
};

const NODE_STATUS_LABELS: Record<string, string> = {
  PENDING: "等待",
  RUNNING: "进行中",
  SUCCEEDED: "完成",
  FAILED: "失败",
  SKIPPED: "跳过"
};

export { NODE_LABELS, PLAN_STATUS_MAP, TASK_STATUS_LABELS, NODE_STATUS_LABELS };
