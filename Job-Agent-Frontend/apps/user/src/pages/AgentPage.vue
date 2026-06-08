<template>
  <main class="page-section agent-page">
    <section class="agent-hero">
      <div>
        <p class="eyebrow">AI 求职助手</p>
        <h1>让 Agent 帮你分析简历、匹配岗位和生成话术</h1>
        <p>
          你可以直接询问简历优化、岗位匹配、HR 沟通话术和求职准备问题。
        </p>
      </div>
    </section>

    <section class="agent-chat-card">
      <div class="agent-message-list" ref="messageListRef">
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

      <form class="agent-input-area" @submit.prevent="sendMessage">
        <textarea
          v-model.trim="inputMessage"
          maxlength="2000"
          placeholder="例如：帮我分析 resumeId=1 的简历，目标岗位是 Java 后端开发"
          @keydown.enter.exact.prevent="sendMessage"
        />

        <button class="primary-button large" type="submit" :disabled="loading || !inputMessage">
          {{ loading ? "发送中..." : "发送" }}
        </button>
      </form>

      <p v-if="errorMessage" class="form-error">{{ errorMessage }}</p>
    </section>
  </main>
</template>

<script setup lang="ts">
import { nextTick, ref } from "vue";
import { ElMessage } from "element-plus";
import { chatWithAgent } from "../api/agent";
import type { AgentChatMessage } from "../api/types";

/**
 * 当前会话ID。
 * 第一次发送为空，后端会自动创建会话并返回 conversationId。
 */
const conversationId = ref<number | null>(null);

/**
 * 页面消息列表。
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
const errorMessage = ref("");
const messageListRef = ref<HTMLElement | null>(null);

/**
 * 快捷提示词。
 * 说明：这里用 resumeId/jobId 是为了方便测试工具调用。
 */
const quickPrompts = [
  "帮我分析 resumeId=1 的简历，目标岗位是 Java 后端开发",
  "帮我分析 resumeId=1 和 jobId=1 的岗位匹配度",
  "帮我根据 resumeId=1 和 jobId=1 生成一段自然风格的 HR 打招呼语",
  "帮我找上海 Java 后端岗位，最低薪资 15000"
];

/**
 * 填充快捷提示词。
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
  loading.value = true;
  errorMessage.value = "";

  await scrollToBottom();

  try {
    const result = await chatWithAgent(conversationId.value, message);

    conversationId.value = result.conversationId;

    messages.value.push({
      role: "ASSISTANT",
      content: result.answer
    });

    await scrollToBottom();
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : "AI 助手调用失败";
    ElMessage.error(errorMessage.value);
  } finally {
    loading.value = false;
  }
}

/**
 * 滚动到底部。
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

.agent-chat-card {
  padding: 18px;
  border-radius: 20px;
  background: #ffffff;
  border: 1px solid #e5e7eb;
}

.agent-message-list {
  height: 520px;
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

@media (max-width: 768px) {
  .agent-input-area {
    flex-direction: column;
  }

  .message-bubble {
    max-width: 86%;
  }
}
</style>