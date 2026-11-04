import { ref } from "vue";
import { ElMessage } from "element-plus";
import { cancelAgentTask, fetchAgentTaskStatus } from "../../api/agent";
import type { AgentProgressEvent } from "../../api/agent";
import type { TaskCard, TaskNodeStatus } from "./taskTypes";
import { NODE_LABELS, NODE_STATUS_LABELS, PLAN_STATUS_MAP } from "./taskLabels";
import { persistTaskPlanId, readPersistedPlanId } from "./taskStorage";

/**
 * 功能: Agent 任务卡片状态与动作（从 AgentPage.vue 抽出）。
 *
 * <p>职责：卡片状态（单一来源）、SSE 事件归一化、快照校正、刷新恢复、
 * 有界轮询、取消、重试和新任务切换。</p>
 *
 * <p>与聊天解耦：不直接读写消息/输入框，重试通过 {@code onRetry} 回调交给页面，
 * 会话 id 通过 {@code getConversationId} 获取，避免两个 composable 互相依赖。</p>
 */
export interface AgentTaskOptions {
  /** 当前会话 id（恢复与落盘用）。 */
  getConversationId: () => number | null;
  /** 当前是否允许重试（例如正在流式输出时禁用）。 */
  canRetry: () => boolean;
  /** 重试动作：由页面发起一次带 planId 与 retry=true 的请求。 */
  onRetry: (planId: number) => Promise<void>;
}

export function useAgentTask(options: AgentTaskOptions) {
const taskCard = ref<TaskCard | null>(null);
const cancelling = ref(false);
let taskPollTimer: ReturnType<typeof setTimeout> | null = null;
let taskPollCount = 0;
/* 页面交互批次号：切换会话/新任务/新请求都会递增，用于丢弃旧回调 */
let taskEpoch = 0;
/*
 * 当前期望展示的任务标识（与 taskCard 是否存在解耦）：
 * 刷新恢复时卡片还是空的，但已知要恢复哪个 planId，
 * 查询返回后应以它为归属校验依据，而不是依赖已存在的卡片。
 */
let expectedPlanId: number | null = null;

function ensureTaskCard(planId: number): TaskCard {
  expectedPlanId = planId;
  if (!taskCard.value || taskCard.value.planId !== planId) {
    taskCard.value = {
      planId,
      runId: null,
      status: "RUNNING",
      // 审查 F6：节点按真实事件/快照生成，独立简历分析不会显示三个 Agent
      nodes: [],
      summaries: [],
      pendingQuestion: "",
      failReason: ""
    };
  }
  return taskCard.value;
}

function updateNode(key: string | null | undefined, status: TaskNodeStatus, summary?: string | null) {
  if (!taskCard.value || !key || !NODE_LABELS[key]) {
    return;
  }
  let node = taskCard.value.nodes.find(item => item.key === key);
  if (!node) {
    node = { key, label: NODE_LABELS[key], status: "PENDING", summary: "" };
    taskCard.value.nodes.push(node);
  }
  // 幂等：不回退已完成的节点；重复事件直接覆盖为同一状态无副作用
  if (node.status === "SUCCEEDED" && status !== "SUCCEEDED") {
    return;
  }
  node.status = status;
  if (summary) {
    node.summary = summary;
  }
}

/**
 * 处理 SSE 进度事件。
 *
 * 说明:
 * 1. 只看当前任务的 planId——其它任务/历史批次迟到的事件直接丢弃；
 * 2. 节点状态各自独立（并行节点可同时 RUNNING），不按全局 seq 串行推进；
 * 3. 最终状态以 done 后的 /task 快照为准，事件只负责即时反馈。
 */
function applyProgressEvent(event: AgentProgressEvent) {
  if (!event) {
    return;
  }
  const planId = event.planId ?? null;
  if (planId) {
    // 旧任务/旧会话的迟到事件：与当前期望任务不一致就丢弃
    if (expectedPlanId !== null && expectedPlanId !== planId) {
      return;
    }
    ensureTaskCard(planId);
    persistTaskPlanId(options.getConversationId(), planId);
  }
  if (!taskCard.value) {
    return;
  }
  // 审查 F5：同一批次取消后，迟到事件不能把状态改回成功/进行中
  if (taskCard.value.status === "CANCELLED"
      && ["task_started", "node_started", "node_completed", "task_completed"].includes(event.event)) {
    return;
  }
  if (event.runId) {
    taskCard.value.runId = event.runId;
  }
  if (event.summary) {
    taskCard.value.summaries.push(event.summary);
    if (taskCard.value.summaries.length > 8) {
      taskCard.value.summaries.splice(0, taskCard.value.summaries.length - 8);
    }
  }
  switch (event.event) {
    case "task_started":
      taskCard.value.status = "RUNNING";
      break;
    case "node_started":
      updateNode(event.nodeKey, "RUNNING", event.summary);
      break;
    case "node_completed":
      updateNode(event.nodeKey, "SUCCEEDED", event.summary);
      break;
    case "waiting_input":
      taskCard.value.status = "WAITING_INPUT";
      taskCard.value.pendingQuestion = event.summary || "";
      break;
    case "task_failed":
      taskCard.value.status = "FAILED";
      taskCard.value.failReason = event.summary || "";
      break;
    case "task_completed":
      taskCard.value.status = event.status === "EMPTY" ? "SUCCEEDED" : "SUCCEEDED";
      break;
    default:
      break;
  }
}

/**
 * 用后端任务快照校正页面状态（刷新恢复 / 事件之后的可信来源）。
 */
async function syncTaskFromServer(planId: number, poll = true) {
  const epoch = taskEpoch;
  try {
    const snapshot = await fetchAgentTaskStatus(planId);
    // 审查 F6：查询返回后必须确认会话/批次/任务仍是当前目标，旧响应直接丢弃
    if (epoch !== taskEpoch || expectedPlanId !== planId) {
      return;
    }
    const status = PLAN_STATUS_MAP[String(snapshot?.status ?? "")] ?? String(snapshot?.status ?? "RUNNING");
    if (taskCard.value?.status === "CANCELLED" && status !== "CANCELLED") {
      return; // 取消是本地已确认的终态，不被迟到快照推翻
    }
    const card = ensureTaskCard(planId);
    card.status = status;
    card.pendingQuestion = snapshot?.pendingQuestion || "";
    card.failReason = snapshot?.failReason || "";
    persistTaskPlanId(options.getConversationId(), planId);

    const runs: any[] = Array.isArray(snapshot?.runs) ? snapshot.runs : [];
    for (const run of runs) {
      const nodeStatus = String(run?.status ?? "");
      const mapped: TaskNodeStatus =
        nodeStatus === "SUCCEEDED"
          ? "SUCCEEDED"
          : nodeStatus === "FAILED"
            ? "FAILED"
            : nodeStatus === "WAITING_INPUT"
              ? "PENDING"
              : "RUNNING";
      if (updateNodeIfNewer(run?.nodeKey, mapped, run?.errorMsg)) {
        card.runId = run?.runId ?? card.runId;
      }
    }

    if (status === "RUNNING" && poll) {
      startTaskPolling(planId);
    } else {
      stopTaskPolling();
    }
  } catch (error) {
    // 查询失败不把任务标记为失败（可能只是网络问题）；保留现有展示
    console.warn("[Job-Agent] 任务状态查询失败", error);
  }
}

// 快照里的节点状态是权威的：允许从 PENDING 推进，但不回退已完成节点
function updateNodeIfNewer(key: string | null | undefined, status: TaskNodeStatus, summary?: string | null) {
  if (!taskCard.value || !key || !NODE_LABELS[key]) {
    return false;
  }
  let node = taskCard.value.nodes.find(item => item.key === key);
  if (!node) {
    node = { key, label: NODE_LABELS[key], status: "PENDING", summary: "" };
    taskCard.value.nodes.push(node);
  }
  if (node.status === "SUCCEEDED") {
    return false;
  }
  node.status = status;
  if (summary) {
    node.summary = summary;
  }
  return true;
}

function startTaskPolling(planId: number) {
  stopTaskPolling();
  taskPollCount = 0;
  const tick = async () => {
    taskPollCount += 1;
    // 有界轮询：最多 40 次（约 2 分钟），终态/切换会话/卸载即停
    if (taskPollCount > 40) {
      stopTaskPolling();
      return;
    }
    await syncTaskFromServer(planId, false);
    const status = taskCard.value?.status;
    if (status === "RUNNING") {
      taskPollTimer = setTimeout(tick, 3000);
    } else {
      stopTaskPolling();
    }
  };
  taskPollTimer = setTimeout(tick, 3000);
}

function stopTaskPolling() {
  if (taskPollTimer) {
    clearTimeout(taskPollTimer);
    taskPollTimer = null;
  }
}

/** 打开会话/刷新后恢复任务卡片（按明确 planId 查询，不重发聊天请求）。 */
async function restoreTaskForConversation(cid: number | null) {
  stopTaskPolling();
  taskEpoch += 1;
  taskCard.value = null;
  const planId = readPersistedPlanId(cid);
  // 先记住"要恢复哪个任务"，卡片由合法快照创建；没有任务则清空期望
  expectedPlanId = planId;
  if (!planId) {
    return;
  }
  await syncTaskFromServer(planId, true);
}

/** 重试：携带原任务标识 + retry=true，复用已完成的上游产物。 */
async function retryTask() {
  if (!taskCard.value?.planId || !options.canRetry()) {
    return;
  }
  const planId = taskCard.value.planId;
  taskCard.value.status = "RUNNING";
  taskCard.value.failReason = "";
  await options.onRetry(planId);
}

/** 取消：仅对稳定父任务标识调用；取消后迟到事件不能把状态改回成功。 */
async function cancelTask() {
  if (!taskCard.value?.planId || cancelling.value) {
    return;
  }
  const planId = taskCard.value.planId;
  cancelling.value = true;
  try {
    await cancelAgentTask(planId);
    if (taskCard.value) {
      taskCard.value.status = "CANCELLED";
      taskCard.value.failReason = "已按你的要求取消，已完成的结果保留。";
    }
    ElMessage.success("已提交取消请求");
    await syncTaskFromServer(planId, false);
    if (taskCard.value) {
      taskCard.value.status = "CANCELLED"; // 迟到事件不能回写成成功
    }
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : "取消失败");
  } finally {
    cancelling.value = false;
  }
}

/** 新任务：只清理续跑标识，不删除历史、不自动取消上一个任务。 */
function startNewTaskFromCard() {
  taskEpoch += 1;
  persistTaskPlanId(options.getConversationId(), null);
  taskCard.value = null;
  expectedPlanId = null;
  stopTaskPolling();
  ElMessage.info("已切换到新任务，下一条消息不会附加到旧任务");
}


  return {
    taskCard,
    cancelling,
    applyProgressEvent,
    syncTaskFromServer,
    restoreTaskForConversation,
    retryTask,
    cancelTask,
    startNewTaskFromCard,
    stopTaskPolling
  };
}
