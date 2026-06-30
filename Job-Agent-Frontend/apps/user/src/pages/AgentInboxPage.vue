<template>
  <main class="page-section inbox-page">
    <section class="inbox-hero">
      <div>
        <p class="eyebrow">Agent Inbox</p>
        <h1>今天需要你确认和推进的事</h1>
        <p>{{ inbox?.summaryText || "Agent 会把 HR 回复、提醒、面试准备、错题复习统一整理到这里。" }}</p>
      </div>
      <button class="primary-button large" type="button" :disabled="loading" @click="loadInbox">
        {{ loading ? "刷新中..." : "刷新待办" }}
      </button>
    </section>

    <section class="summary-grid">
      <article class="summary-card high">
        <span>高优先级</span>
        <strong>{{ inbox?.highPriorityCount || 0 }}</strong>
      </article>
      <article class="summary-card">
        <span>全部待办</span>
        <strong>{{ inbox?.totalCount || 0 }}</strong>
      </article>
      <article class="summary-card warning">
        <span>已到期</span>
        <strong>{{ inbox?.dueCount || 0 }}</strong>
      </article>
      <article class="summary-card normal">
        <span>普通建议</span>
        <strong>{{ inbox?.normalCount || 0 }}</strong>
      </article>
    </section>

    <section class="toolbar">
      <label>
        <span>优先级</span>
        <select v-model="priorityFilter">
          <option value="">全部</option>
          <option value="HIGH">高优先级</option>
          <option value="NORMAL">普通</option>
          <option value="LOW">低优先级</option>
        </select>
      </label>
      <label>
        <span>类型</span>
        <select v-model="typeFilter">
          <option value="">全部</option>
          <option value="HR_REPLY_CONFIRM">HR 回复待确认</option>
          <option value="REMINDER">求职提醒</option>
          <option value="INTERVIEW_PREPARE">面试准备</option>
          <option value="PREPARE_REVIEW">面试材料复习</option>
          <option value="WRONG_QUESTION_REVIEW">错题复习</option>
          <option value="LEARNING_PLAN">学习计划</option>
        </select>
      </label>
    </section>

    <p v-if="errorMessage" class="form-error">{{ errorMessage }}</p>
    <p v-if="loading" class="empty-state">Agent 正在整理今天的待办...</p>
    <el-empty v-else-if="filteredItems.length === 0" description="今天暂无待处理事项" />

    <section v-else class="inbox-list">
      <article
        v-for="item in filteredItems"
        :key="item.itemKey"
        class="inbox-card"
        :class="item.priority.toLowerCase()"
      >
        <div class="card-main">
          <div class="title-row">
            <el-tag :type="priorityTagType(item.priority)">
              {{ priorityText(item.priority) }}
            </el-tag>
            <span class="type-pill">{{ item.itemTypeDesc || item.itemType }}</span>
          </div>
          <h3>{{ item.title }}</h3>
          <p>{{ item.description || "暂无说明" }}</p>
          <div class="meta-line">
            <span v-if="item.companyName">{{ item.companyName }}</span>
            <span v-if="item.jobTitle">{{ item.jobTitle }}</span>
            <span v-if="item.dueTime">时间：{{ item.dueTime }}</span>
          </div>
        </div>

        <button class="primary-button" type="button" @click="goTarget(item)">
          {{ item.actionText || "去处理" }}
        </button>
      </article>
    </section>
  </main>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import { getTodayAgentInbox } from "../api/agentInbox";
import type { AgentInboxInfo, AgentInboxItemInfo } from "../api/types";

const router = useRouter();
const inbox = ref<AgentInboxInfo | null>(null);
const loading = ref(false);
const errorMessage = ref("");
const priorityFilter = ref("");
const typeFilter = ref("");

const filteredItems = computed(() => {
  const source = inbox.value?.items || [];
  return source.filter(item => {
    if (priorityFilter.value && item.priority !== priorityFilter.value) {
      return false;
    }
    if (typeFilter.value && item.itemType !== typeFilter.value) {
      return false;
    }
    return true;
  });
});

onMounted(loadInbox);

async function loadInbox() {
  loading.value = true;
  errorMessage.value = "";
  try {
    inbox.value = await getTodayAgentInbox();
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : "Agent 待办加载失败";
    ElMessage.error(errorMessage.value);
  } finally {
    loading.value = false;
  }
}

function goTarget(item: AgentInboxItemInfo) {
  router.push(item.targetPath || "/follow-up");
}

function priorityText(priority: string) {
  if (priority === "HIGH") return "高";
  if (priority === "LOW") return "低";
  return "普通";
}

function priorityTagType(priority: string) {
  if (priority === "HIGH") return "danger";
  if (priority === "LOW") return "info";
  return "warning";
}
</script>

<style scoped>
.inbox-page {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.inbox-hero,
.toolbar,
.summary-card,
.inbox-card {
  border: 1px solid #e5e7eb;
  border-radius: 16px;
  background: #ffffff;
}

.inbox-hero {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 18px;
  padding: 24px;
  background: linear-gradient(135deg, #eef2ff, #ffffff);
}

.inbox-hero h1 {
  margin: 6px 0;
  color: #111827;
}

.inbox-hero p {
  margin: 0;
  color: #6b7280;
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
}

.summary-card {
  padding: 18px;
}

.summary-card span {
  color: #6b7280;
}

.summary-card strong {
  display: block;
  margin-top: 8px;
  color: #111827;
  font-size: 28px;
}

.summary-card.high {
  background: #fef2f2;
  border-color: #fecaca;
}

.summary-card.warning {
  background: #fff7ed;
  border-color: #fed7aa;
}

.summary-card.normal {
  background: #eff6ff;
  border-color: #bfdbfe;
}

.toolbar {
  display: grid;
  grid-template-columns: repeat(2, 220px);
  gap: 14px;
  padding: 18px;
}

.toolbar label {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.toolbar span {
  color: #374151;
  font-size: 13px;
  font-weight: 700;
}

.toolbar select {
  padding: 10px 12px;
  border: 1px solid #d1d5db;
  border-radius: 12px;
  font: inherit;
}

.inbox-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.inbox-card {
  display: grid;
  grid-template-columns: 1fr auto;
  align-items: center;
  gap: 16px;
  padding: 18px;
}

.inbox-card.high {
  border-color: #fecaca;
}

.title-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.type-pill {
  padding: 4px 9px;
  border-radius: 999px;
  background: #f1f5f9;
  color: #475569;
  font-size: 12px;
  font-weight: 700;
}

.inbox-card h3 {
  margin: 10px 0 6px;
  color: #111827;
}

.inbox-card p {
  margin: 0;
  color: #4b5563;
  line-height: 1.6;
}

.meta-line {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 10px;
  color: #6b7280;
  font-size: 13px;
}

@media (max-width: 900px) {
  .summary-grid,
  .toolbar,
  .inbox-card {
    grid-template-columns: 1fr;
  }

  .inbox-hero {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
