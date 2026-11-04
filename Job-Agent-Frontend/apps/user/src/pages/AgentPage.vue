<template>
  <main class="page-section agent-page">
    <section class="agent-hero">
      <div>
        <p class="eyebrow">AI 求职助手</p>
        <h1>让 Agent 帮你分析简历、匹配岗位和生成话术</h1>
        <p>你可以直接询问简历优化、岗位匹配、HR 沟通话术和求职准备问题。</p>
      </div>
    </section>

    <section class="agent-layout">
      <aside class="conversation-sidebar">
        <div class="sidebar-header">
          <h3>历史会话</h3>
          <button class="primary-button" type="button" @click="startNewConversation">
            新建
          </button>
        </div>

        <p v-if="conversationLoading" class="empty-state">正在加载会话...</p>

        <div v-else-if="!conversations.length" class="empty-state">
          暂无历史会话
        </div>

        <div v-else class="conversation-list">
          <button
            v-for="conversation in conversations"
            :key="conversation.id"
            type="button"
            class="conversation-item"
            :class="{ active: conversation.id === conversationId }"
            @click="openConversation(conversation.id)"
          >
            <span>{{ conversation.title || "新的求职对话" }}</span>
            <small>{{ conversation.updateTime || conversation.createTime || "-" }}</small>
          </button>
        </div>

        <button
          v-if="conversationId"
          class="danger-button delete-conversation-button"
          type="button"
          @click="removeCurrentConversation"
        >
          删除当前会话
        </button>
      </aside>

      <section class="agent-chat-card">
        <!-- 第三阶段：轻量任务卡片（复用现有样式语言，不重设计页面） -->
        <div v-if="taskCard" class="agent-task-card">
          <div class="task-head">
            <strong>任务进度</strong>
            <span class="task-status" :class="taskCard.status.toLowerCase()">
              {{ TASK_STATUS_LABELS[taskCard.status] || taskCard.status }}
            </span>
          </div>

          <div class="task-nodes">
            <span
              v-for="node in taskCard.nodes"
              :key="node.key"
              class="task-node"
              :class="node.status.toLowerCase()"
            >
              {{ node.label }} · {{ NODE_STATUS_LABELS[node.status] || node.status }}
            </span>
          </div>

          <p v-if="taskCard.summaries.length" class="task-summary">
            {{ taskCard.summaries[taskCard.summaries.length - 1] }}
          </p>
          <p v-if="taskCard.status === 'WAITING_INPUT' && taskCard.pendingQuestion" class="task-question">
            {{ taskCard.pendingQuestion }}
          </p>
          <p v-if="taskCard.status === 'CANCELLED' && taskCard.failReason" class="task-summary">
            {{ taskCard.failReason }}
          </p>
          <p v-else-if="taskCard.failReason" class="task-error">{{ taskCard.failReason }}</p>

          <div class="task-actions">
            <button
              v-if="taskCard.status === 'FAILED'"
              class="primary-button"
              type="button"
              :disabled="loading"
              @click="retryTask"
            >
              重试
            </button>
            <button
              v-if="taskCard.status === 'RUNNING' || taskCard.status === 'WAITING_INPUT'"
              class="danger-button"
              type="button"
              :disabled="cancelling"
              @click="cancelTask"
            >
              {{ cancelling ? "取消中…" : "取消任务" }}
            </button>
            <button class="ghost-button" type="button" @click="startNewTaskFromCard">
              新任务
            </button>
          </div>
        </div>

        <div ref="messageListRef" class="agent-message-list">
          <div
            v-for="(message, index) in messages"
            :key="index"
            class="agent-message"
            :class="message.role === 'USER' ? 'user' : 'assistant'"
          >
            <div class="message-avatar">
              {{ message.role === "USER" ? "我" : "AI" }}
            </div>

            <div class="message-bubble">
              <pre>{{ message.content }}</pre>
            </div>
          </div>

          <div v-if="loading" class="agent-message assistant">
            <div class="message-avatar">AI</div>
            <div class="message-bubble">
              <pre>正在思考中...</pre>
            </div>
          </div>
        </div>

        <div v-if="pendingConfirmation.planId" class="confirmation-panel">
          <div>
            <strong>需要确认后继续执行</strong>
            <p>{{ pendingConfirmation.message }}</p>
            <small>工具：{{ pendingConfirmation.toolNames.join("、") }}</small>
          </div>
          <div class="confirmation-actions">
            <button class="secondary-button" type="button" :disabled="loading" @click="cancelConfirmation">
              取消
            </button>
            <button class="primary-button" type="button" :disabled="loading" @click="confirmToolExecution">
              确认执行
            </button>
          </div>
        </div>

        <form class="agent-input-area" @submit.prevent="sendMessage">
          <textarea
            v-model.trim="inputMessage"
            maxlength="2000"
            placeholder="例如：帮我分析 resumeId=1 的简历，求职方向是 Java 后端开发"
            @keydown.enter.exact.prevent="sendMessage"
          />

          <button
            class="primary-button large"
            type="submit"
            :disabled="loading || !inputMessage"
          >
            {{ loading ? "发送中..." : "发送" }}
          </button>
        </form>

        <p v-if="errorMessage" class="form-error">{{ errorMessage }}</p>
      </section>
    </section>
  </main>
</template>

<script setup lang="ts">
import {
  NODE_LABELS,
  NODE_STATUS_LABELS,
  PLAN_STATUS_MAP,
  TASK_STATUS_LABELS
} from "../composables/agent/taskLabels";
import { nextTick, onMounted, onUnmounted, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import {
  chatWithAgent,
  streamChatWithAgent,
  deleteAgentConversation,
  fetchAgentTaskStatus,
  cancelAgentTask,
  listAgentConversations,
  listAgentMessages
} from "../api/agent";
import type { AgentProgressEvent } from "../api/agent";
import type { AgentChatPayload } from "../api/agent";
import type {
  AgentChatMessage,
  AgentConversationInfo,
  AgentMessageInfo
} from "../api/types";

/**
 * 当前会话ID。
 * null 表示当前是一个新会话，第一次发送消息时后端会自动创建会话。
 */
const conversationId = ref<number | null>(null);

/**
 * 左侧会话列表。
 */
const conversations = ref<AgentConversationInfo[]>([]);

/* ===================== 第三阶段：任务进度与任务控制 ===================== */

type TaskNodeStatus = "PENDING" | "RUNNING" | "SUCCEEDED" | "FAILED" | "SKIPPED";

interface TaskNodeView {
  key: string;
  label: string;
  status: TaskNodeStatus;
  summary: string;
}

interface TaskCard {
  planId: number | null;
  runId: number | null;
  /** 规范化后的状态：RUNNING / WAITING_INPUT / SUCCEEDED / FAILED / CANCELLED */
  status: string;
  nodes: TaskNodeView[];
  summaries: string[];
  pendingQuestion: string;
  failReason: string;
}


/* 后端计划状态 → 页面状态，集中一处映射，避免各组件各写一套 */



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

function taskStorageKey(cid: number | null) {
  return `jobAgentTask:${cid ?? "new"}`;
}

function persistTaskPlanId(cid: number | null, planId: number | null) {
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

function readPersistedPlanId(cid: number | null): number | null {
  try {
    const raw = window.localStorage.getItem(taskStorageKey(cid));
    const parsed = raw ? Number(raw) : NaN;
    return Number.isFinite(parsed) ? parsed : null;
  } catch {
    return null;
  }
}

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
    persistTaskPlanId(conversationId.value, planId);
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
    persistTaskPlanId(conversationId.value, planId);

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
  if (!taskCard.value?.planId || loading.value) {
    return;
  }
  const planId = taskCard.value.planId;
  taskCard.value.status = "RUNNING";
  taskCard.value.failReason = "";
  messages.value.push({ role: "USER", content: "重试该任务" });
  loading.value = true;
  errorMessage.value = "";
  await runAgentTurn({ conversationId: conversationId.value, planId, message: "重试该任务", retry: true });
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
  persistTaskPlanId(conversationId.value, null);
  taskCard.value = null;
  expectedPlanId = null;
  stopTaskPolling();
  ElMessage.info("已切换到新任务，下一条消息不会附加到旧任务");
}

/**
 * 右侧聊天消息。
 */
const messages = ref<AgentChatMessage[]>([
  {
    role: "ASSISTANT",
    content:
      "你好，我是 Job-Agent 智能求职助手。你可以问我：分析简历、匹配岗位、生成 HR 打招呼语、搜索岗位、准备面试。"
  }
]);

const inputMessage = ref("");
const loading = ref(false);
const conversationLoading = ref(false);
const errorMessage = ref("");
const messageListRef = ref<HTMLElement | null>(null);

/**
 * 待用户确认的工具执行信息。
 *
 * 说明:
 * 1. 后端返回 requiresUserConfirmation=true 时写入这里。
 * 2. 用户点击“确认执行”后，前端把 planId 和 confirmedToolNames 原样传回后端。
 * 3. 这样后端会继续执行同一份计划，而不是把“确认”当作一条新任务重新规划。
 */
const pendingConfirmation = ref<{
  planId: number | null;
  toolNames: string[];
  message: string;
}>({
  planId: null,
  toolNames: [],
  message: ""
});

onMounted(async () => {
  await loadConversations();
});

/**
 * 加载历史会话列表。
 */
async function loadConversations() {
  conversationLoading.value = true;

  try {
    conversations.value = await listAgentConversations();
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : "会话列表加载失败");
  } finally {
    conversationLoading.value = false;
  }
}

/**
 * 打开某个历史会话。
 *
 * @param id 会话ID
 */
async function openConversation(id: number) {
  conversationId.value = id;
  errorMessage.value = "";

  try {
    const historyMessages = await listAgentMessages(id);

    messages.value = convertHistoryMessages(historyMessages);

    if (!messages.value.length) {
      messages.value = [
        {
          role: "ASSISTANT",
          content: "这个会话还没有消息，你可以继续提问。"
        }
      ];
    }

    await scrollToBottom();
    // 第三阶段：进入会话时按已保存的 planId 恢复任务卡片（不重发聊天请求）
    await restoreTaskForConversation(id);
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : "历史消息加载失败";
    ElMessage.error(errorMessage.value);
  }
}

/**
 * 新建会话。
 *
 * 说明:
 * 1. 不需要立刻调用后端创建会话。
 * 2. 第一次发送消息时，后端会自动创建 conversation。
 */
function startNewConversation() {
  conversationId.value = null;
  inputMessage.value = "";
  errorMessage.value = "";
  // 第三阶段：新会话不继承旧任务的续跑标识
  taskCard.value = null;
  stopTaskPolling();
  clearPendingConfirmation();
  messages.value = [
    {
      role: "ASSISTANT",
      content:
        "已开启新的对话。你可以让我帮你分析简历、匹配岗位、生成 HR 打招呼语或准备面试。"
    }
  ];
}

/**
 * 删除当前会话。
 */
async function removeCurrentConversation() {
  if (!conversationId.value) {
    return;
  }

  try {
    await ElMessageBox.confirm("确定删除当前会话吗？删除后不可在列表中查看。", "删除会话", {
      type: "warning",
      confirmButtonText: "删除",
      cancelButtonText: "取消"
    });

    await deleteAgentConversation(conversationId.value);

    ElMessage.success("会话已删除");

    await loadConversations();
    startNewConversation();
  } catch (error) {
    if (error === "cancel" || error === "close") {
      return;
    }

    ElMessage.error(error instanceof Error ? error.message : "会话删除失败");
  }
}

/**
 * 历史消息转换为页面展示消息。
 *
 * @param historyMessages 后端历史消息
 */
function convertHistoryMessages(historyMessages: AgentMessageInfo[]) {
  return historyMessages
    .filter(item => item.role === "USER" || item.role === "ASSISTANT")
    .map(item => ({
      role: item.role as "USER" | "ASSISTANT",
      content: item.content
    }));
}

/**
 * 发送消息。
 */
async function sendMessage() {
  const message = inputMessage.value.trim();

  if (!message) {
    return;
  }

  messages.value.push({
    role: "USER",
    content: message
  });

  inputMessage.value = "";
  clearPendingConfirmation();
  loading.value = true;
  errorMessage.value = "";

  await scrollToBottom();
  /*
   * 第三阶段：任务处于 WAITING_INPUT 时，补充消息带回原 planId 续跑原任务，
   * 不被当作新任务重新分类/重新创建。
   */
  const payload: AgentChatPayload = { conversationId: conversationId.value, message };
  if (taskCard.value?.status === "WAITING_INPUT" && taskCard.value.planId) {
    payload.planId = taskCard.value.planId;
  }
  await runAgentTurn(payload);
}

/**
 * 用户确认执行需要确认的工具。
 *
 * 方法步骤:
 * 1. 校验当前确实存在待确认计划。
 * 2. 在页面中追加一条用户确认消息，保证聊天上下文可读。
 * 3. 请求后端时带上 planId 和 confirmedToolNames，继续执行同一份计划。
 * 4. 请求成功后清理待确认状态，避免重复确认。
 */
async function confirmToolExecution() {
  if (!pendingConfirmation.value.planId || !pendingConfirmation.value.toolNames.length) {
    return;
  }

  const confirmedPlanId = pendingConfirmation.value.planId;
  const confirmedToolNames = [...pendingConfirmation.value.toolNames];

  messages.value.push({
    role: "USER",
    content: "确认执行"
  });

  clearPendingConfirmation();
  loading.value = true;
  errorMessage.value = "";
  await scrollToBottom();

  await runAgentTurn({
    conversationId: conversationId.value,
    planId: confirmedPlanId,
    message: "确认执行",
    confirmedToolNames
  });
}

/**
 * 执行一轮对话（任务 G5：流式优先，失败回退阻塞）。
 *
 * 方法步骤:
 * 1. 先插入一个空的助手气泡，流式 token 逐字追加，用户立刻看到“正在输出”；
 * 2. 流式结束（done）后，用服务端最终答案校正气泡内容——输出护栏可能在流式后改写文本；
 * 3. 流式不可用（旧浏览器/网络/服务端降级失败）时移除空气泡，回退阻塞接口，功能不降级。
 *
 * @param payload 对话请求参数
 */
async function runAgentTurn(payload: AgentChatPayload) {
  const bubble: AgentChatMessage = { role: "ASSISTANT", content: "" };
  messages.value.push(bubble);

  try {
    const result = await streamChatWithAgent(
      payload,
      token => {
        bubble.content += token;
        scrollToBottom();
      },
      applyProgressEvent
    );
    // 流式结束：以服务端最终答案为准
    bubble.content = result.answer;
    await applyAgentResultState(result);
    // 最终任务状态以快照为准（不能只看 token 是否结束）
    if (result.planId) {
      await syncTaskFromServer(result.planId, true);
    }
  } catch (streamError) {
    /*
     * 审查 F2：已收到任务标识时，断流优先查询原任务状态，
     * 不能盲目再提交一次阻塞请求（服务端可能仍在执行）。
     */
    const knownPlanId = payload.planId ?? taskCard.value?.planId ?? null;
    if (knownPlanId) {
      console.warn("[Job-Agent] 流式中断，改为查询任务状态", streamError);
      bubble.content = bubble.content || "连接中断，正在确认任务状态…";
      await syncTaskFromServer(knownPlanId, true);
      const statusText = TASK_STATUS_LABELS[taskCard.value?.status ?? ""] ?? taskCard.value?.status ?? "未知";
      bubble.content = `连接中断，任务当前状态：${statusText}。可稍后刷新查看结果，不会重复提交。`;
      loading.value = false;
      return;
    }
    // 流式失败且没有任务标识：回退阻塞接口（先移除空气泡，避免残留半截内容）
    console.warn("[Job-Agent] 流式对话失败，回退阻塞接口", streamError);
    messages.value.splice(messages.value.indexOf(bubble), 1);
    try {
      const result = await chatWithAgent(payload);
      messages.value.push({ role: "ASSISTANT" as const, content: result.answer });
      await applyAgentResultState(result);
    } catch (error) {
      errorMessage.value = error instanceof Error ? error.message : "AI 助手调用失败";
      ElMessage.error(errorMessage.value);
    }
  } finally {
    loading.value = false;
  }
}

/**
 * 取消本轮待确认工具。
 */
function cancelConfirmation() {
  clearPendingConfirmation();
  messages.value.push({
    role: "ASSISTANT",
    content: "已取消本次工具执行。你可以重新描述需求，或换一个不需要确认的操作。"
  });
  scrollToBottom();
}

/**
 * 统一应用后端 AI 助手返回结果到页面状态（消息内容由调用方写入）。
 *
 * @param result 后端返回结果
 */
async function applyAgentResultState(result: Awaited<ReturnType<typeof chatWithAgent>>) {
  /*
   * 后端第一次对话会自动创建 conversationId。
   */
  conversationId.value = result.conversationId;

  if (result.requiresUserConfirmation && result.planId && result.requiredConfirmationToolNames?.length) {
    pendingConfirmation.value = {
      planId: result.planId,
      toolNames: result.requiredConfirmationToolNames,
      message: result.confirmationMessage || "该操作需要你确认后才能继续执行。"
    };
  } else {
    clearPendingConfirmation();
  }

  /*
   * 发送成功后刷新左侧会话列表。
   */
  await loadConversations();
  await scrollToBottom();
}

/**
 * 清理待确认状态。
 */
function clearPendingConfirmation() {
  pendingConfirmation.value = {
    planId: null,
    toolNames: [],
    message: ""
  };
}

/**
 * 滚动到消息底部。
 */
async function scrollToBottom() {
  await nextTick();

  if (messageListRef.value) {
    messageListRef.value.scrollTop = messageListRef.value.scrollHeight;
  }
}

/**
 * 第三阶段：组件卸载时清理轮询计时器，避免旧回调写已卸载组件。
 */
onUnmounted(() => {
  stopTaskPolling();
});

/**
 * 第三阶段：首次进入页面时恢复当前会话的任务卡片（按 planId 查询，不重发请求）。
 */
onMounted(() => {
  restoreTaskForConversation(conversationId.value);
});
</script>

<style scoped>
.agent-page {
  display: flex;
  flex-direction: column;
  gap: 18px;
  height: calc(100vh - 68px);
  min-height: 0;
  overflow: hidden;
  padding: 28px 0 24px;
}

.agent-hero {
  padding: 28px;
  border-radius: 24px;
  background: linear-gradient(135deg, #eff6ff, #ffffff);
  border: 1px solid #dbeafe;
}

.agent-hero h1 {
  margin: 6px 0;
  color: #111827;
}

.agent-hero p {
  color: #6b7280;
}

.agent-layout {
  display: grid;
  grid-template-columns: 280px minmax(0, 1fr);
  gap: 16px;
  flex: 1;
  min-height: 0;
}

.conversation-sidebar {
  padding: 16px;
  border-radius: 20px;
  background: #ffffff;
  border: 1px solid #e5e7eb;
  min-height: 0;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.sidebar-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 14px;
}

.sidebar-header h3 {
  margin: 0;
  color: #111827;
}

.conversation-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding-right: 4px;
}

.conversation-item {
  width: 100%;
  padding: 10px;
  border-radius: 12px;
  border: 1px solid #e5e7eb;
  background: #f9fafb;
  text-align: left;
  cursor: pointer;
}

.conversation-item.active {
  border-color: #2563eb;
  background: #eff6ff;
}

.conversation-item span {
  display: block;
  color: #111827;
  font-weight: 600;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.conversation-item small {
  display: block;
  margin-top: 4px;
  color: #6b7280;
  font-size: 12px;
}

.delete-conversation-button {
  margin-top: 14px;
}

.agent-chat-card {
  display: flex;
  flex-direction: column;
  min-height: 0;
  overflow: hidden;
  padding: 18px;
  border-radius: 20px;
  background: #ffffff;
  border: 1px solid #e5e7eb;
}

.agent-message-list {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 12px;
  border-radius: 16px;
  background: #f8fafc;
}

.agent-message {
  display: flex;
  gap: 10px;
  margin-bottom: 14px;
}

.agent-message.user {
  flex-direction: row-reverse;
}

.message-avatar {
  width: 36px;
  height: 36px;
  flex: 0 0 auto;
  border-radius: 50%;
  background: #2563eb;
  color: #ffffff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
}

.agent-message.user .message-avatar {
  background: #10b981;
}

.message-bubble {
  max-width: 72%;
  padding: 12px 14px;
  border-radius: 14px;
  background: #ffffff;
  border: 1px solid #e5e7eb;
}

.agent-message.user .message-bubble {
  background: #ecfdf5;
  border-color: #bbf7d0;
}

.message-bubble pre {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
  font-family: inherit;
  line-height: 1.7;
  color: #374151;
}

.quick-prompts {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin: 14px 0;
}

.confirmation-panel {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 14px;
  margin-bottom: 14px;
  padding: 12px 14px;
  border: 1px solid #fed7aa;
  border-radius: 14px;
  background: #fff7ed;
}

.confirmation-panel p {
  margin: 4px 0;
  color: #9a3412;
}

.confirmation-panel small {
  color: #6b7280;
}

.confirmation-actions {
  display: flex;
  gap: 8px;
  flex: 0 0 auto;
}

.agent-input-area {
  display: flex;
  gap: 12px;
}

.agent-input-area textarea {
  flex: 1;
  min-height: 86px;
  resize: vertical;
  padding: 12px;
  border-radius: 14px;
  border: 1px solid #d1d5db;
  font: inherit;
}

@media (max-width: 900px) {
  .agent-page {
    height: auto;
    min-height: calc(100vh - 68px);
    overflow: visible;
    padding: 28px 0 40px;
  }

  .agent-layout {
    grid-template-columns: 1fr;
    flex: none;
  }

  .conversation-sidebar {
    max-height: 360px;
  }

  .agent-chat-card {
    min-height: 620px;
  }
}

@media (max-width: 768px) {
  .agent-input-area {
    flex-direction: column;
  }

  .confirmation-panel {
    align-items: stretch;
    flex-direction: column;
  }

  .message-bubble {
    max-width: 86%;
  }

  /* 第三阶段：任务卡片 */
  .agent-task-card {
    margin: 12px 16px 0;
    padding: 12px 14px;
    border: 1px solid #e5e7eb;
    border-radius: 12px;
    background: #fafbfc;
    font-size: 13px;
    color: #374151;

    .task-head {
      display: flex;
      align-items: center;
      justify-content: space-between;
      margin-bottom: 8px;
    }

    .task-status {
      font-size: 12px;
      padding: 2px 8px;
      border-radius: 999px;
      background: #eef2ff;
      color: #4338ca;

      &.waiting_input { background: #fef3c7; color: #92400e; }
      &.failed { background: #fee2e2; color: #b91c1c; }
      &.cancelled { background: #f3f4f6; color: #4b5563; }
      &.succeeded { background: #dcfce7; color: #166534; }
    }

    .task-nodes {
      display: flex;
      flex-wrap: wrap;
      gap: 8px;
      margin-bottom: 8px;
    }

    .task-node {
      padding: 2px 8px;
      border-radius: 999px;
      background: #f3f4f6;
      color: #6b7280;

      &.running { background: #e0f2fe; color: #075985; }
      &.succeeded { background: #dcfce7; color: #166534; }
      &.failed { background: #fee2e2; color: #b91c1c; }
    }

    .task-summary { margin: 4px 0; color: #4b5563; }
    .task-question { margin: 6px 0; color: #92400e; }
    .task-error { margin: 6px 0; color: #b91c1c; }

    .task-actions {
      display: flex;
      gap: 8px;
      margin-top: 8px;
    }
  }
}
</style>
