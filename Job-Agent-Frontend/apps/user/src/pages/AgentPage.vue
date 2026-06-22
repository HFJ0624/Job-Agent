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

        <div class="quick-prompts">
          <button
            v-for="prompt in quickPrompts"
            :key="prompt"
            type="button"
            class="secondary-button"
            @click="fillPrompt(prompt)"
          >
            {{ prompt }}
          </button>
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
import { nextTick, onMounted, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import {
  chatWithAgent,
  deleteAgentConversation,
  listAgentConversations,
  listAgentMessages
} from "../api/agent";
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

/**
 * 快捷提示词。
 * 说明:
 * 1. 当前 ID 已经改成自增 ID，所以可以用 resumeId=1、jobId=1 方便测试。
 * 2. 后续可以再改成“默认简历”或“简历名称”。
 */
const quickPrompts = [
  "帮我分析 resumeId=1 的简历，求职方向是 Java 后端开发",
  "帮我分析 resumeId=1 和 jobId=1 的岗位匹配度",
  "帮我根据 resumeId=1 和 jobId=1 生成一段自然风格的 HR 打招呼语",
  "帮我找上海 Java 后端岗位，最低薪资 15000"
];

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
 * 填充快捷提示词。
 *
 * @param prompt 快捷提示词
 */
function fillPrompt(prompt: string) {
  inputMessage.value = prompt;
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

  try {
    const result = await chatWithAgent({
      conversationId: conversationId.value,
      message
    });

    await handleAgentResult(result);
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : "AI 助手调用失败";
    ElMessage.error(errorMessage.value);
  } finally {
    loading.value = false;
  }
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

  try {
    const result = await chatWithAgent({
      conversationId: conversationId.value,
      planId: confirmedPlanId,
      message: "确认执行",
      confirmedToolNames
    });

    await handleAgentResult(result);
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : "AI 助手调用失败";
    ElMessage.error(errorMessage.value);
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
 * 统一处理后端 AI 助手返回结果。
 *
 * @param result 后端返回结果
 */
async function handleAgentResult(result: Awaited<ReturnType<typeof chatWithAgent>>) {
  /*
   * 后端第一次对话会自动创建 conversationId。
   */
  conversationId.value = result.conversationId;

  messages.value.push({
    role: "ASSISTANT",
    content: result.answer
  });

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
}
</style>
