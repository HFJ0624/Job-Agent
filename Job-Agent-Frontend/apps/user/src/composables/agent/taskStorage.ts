/**
 * 功能: Agent 任务的本地续跑标识（从 AgentPage.vue 抽出）。
 *
 * 只负责"按会话存取 planId"，用于刷新/重进会话时恢复任务卡片；
 * 不依赖 Vue 状态，便于在 composable 与页面之间复用。
 */

export function taskStorageKey(cid: number | null) {
  return `jobAgentTask:${cid ?? "new"}`;
}

export function persistTaskPlanId(cid: number | null, planId: number | null) {
  try {
    if (planId) {
      window.localStorage.setItem(taskStorageKey(cid), String(planId));
    } else {
      window.localStorage.removeItem(taskStorageKey(cid));
    }
  } catch {
    /* 本地存储不可用时忽略，不影响主流程 */
  }
}

export function readPersistedPlanId(cid: number | null): number | null {
  try {
    const raw = window.localStorage.getItem(taskStorageKey(cid));
    const parsed = raw ? Number(raw) : NaN;
    return Number.isFinite(parsed) ? parsed : null;
  } catch {
    return null;
  }
}

