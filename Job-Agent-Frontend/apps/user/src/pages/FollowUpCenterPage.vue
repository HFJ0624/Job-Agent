<template>
  <main class="page-section follow-up-page">
    <section class="follow-hero">
      <div>
        <p class="eyebrow">求职跟进 Agent</p>
        <h1>今天该推进哪些岗位</h1>
        <p>把投递、提醒、面试准备和模拟面试放到一个视图里，让 Agent 帮你排出下一步。</p>
      </div>
      <button class="primary-button large" type="button" :disabled="loading" @click="loadCenter">
        {{ loading ? "刷新中..." : "刷新跟进中心" }}
      </button>
    </section>

    <section class="summary-grid">
      <article class="summary-card">
        <span>求职记录</span>
        <strong>{{ center?.applicationStats?.totalCount || 0 }}</strong>
      </article>
      <article class="summary-card warning">
        <span>待处理提醒</span>
        <strong>{{ center?.reminderStats?.pendingCount || 0 }}</strong>
      </article>
      <article class="summary-card danger">
        <span>已到期提醒</span>
        <strong>{{ center?.reminderStats?.dueCount || 0 }}</strong>
      </article>
      <article class="summary-card primary">
        <span>面试中</span>
        <strong>{{ center?.applicationStats?.interviewingCount || 0 }}</strong>
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
        <span>关键词</span>
        <input v-model.trim="keyword" placeholder="搜索公司或岗位" />
      </label>
    </section>

    <p v-if="errorMessage" class="form-error">{{ errorMessage }}</p>
    <p v-if="loading" class="empty-state">正在加载求职跟进建议...</p>
    <p v-else-if="filteredItems.length === 0" class="empty-state">暂无需要展示的求职跟进事项。</p>

    <section v-else class="follow-list">
      <article v-for="item in filteredItems" :key="item.application.id" class="follow-card" :class="item.priority.toLowerCase()">
        <div class="card-main">
          <div class="title-row">
            <h3>{{ item.application.jobTitle || "未命名岗位" }}</h3>
            <span class="priority-pill">{{ priorityText(item.priority) }}</span>
            <span class="status-pill">{{ item.application.statusText || item.application.status }}</span>
          </div>

          <p class="meta-line">
            {{ item.application.companyName || "未知公司" }} · {{ item.application.city || "城市待补充" }} ·
            {{ item.application.salaryText || "薪资面议" }}
          </p>
          <p class="time-line">
            投递：{{ item.application.applyTime || "暂无" }}
            · 面试：{{ item.application.interviewTime || "暂无" }}
            · 跟进：{{ item.application.nextFollowTime || "暂无" }}
          </p>
          <p class="reason-line">{{ item.priorityReason }}</p>

          <div v-if="item.pendingReminders.length" class="reminder-box">
            <h4>待处理提醒</h4>
            <div v-for="reminder in item.pendingReminders" :key="reminder.id" class="reminder-row">
              <div>
                <strong>{{ reminder.reminderTitle }}</strong>
                <p>{{ reminder.reminderContent || "暂无提醒内容" }}</p>
                <small>{{ reminder.remindTime || "暂无提醒时间" }}</small>
              </div>
              <button class="secondary-button" type="button" @click="completeReminder(reminder.id)">完成</button>
            </div>
          </div>
        </div>

        <aside class="action-panel">
          <h4>Agent 建议</h4>
          <button
            v-for="action in item.suggestedActions"
            :key="`${item.application.id}-${action.actionCode}`"
            type="button"
            class="action-button"
            :class="action.priority.toLowerCase()"
            @click="goAction(action)"
          >
            <span>{{ action.title }}</span>
            <small>{{ action.description }}</small>
            <b>{{ action.buttonText }}</b>
          </button>
        </aside>
      </article>
    </section>
  </main>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import { getFollowUpCenter } from "../api/followUpAgent";
import { markReminderDone } from "../api/reminder";
import type { FrontFollowUpActionInfo, FrontFollowUpApplicationInfo, FrontFollowUpCenterInfo } from "../api/types";

const router = useRouter();
const center = ref<FrontFollowUpCenterInfo | null>(null);
const loading = ref(false);
const errorMessage = ref("");
const priorityFilter = ref("");
const keyword = ref("");

const filteredItems = computed(() => {
  const source = center.value?.applications || [];
  return source.filter(item => {
    if (priorityFilter.value && item.priority !== priorityFilter.value) {
      return false;
    }
    if (!keyword.value) {
      return true;
    }
    const text = `${item.application.companyName || ""} ${item.application.jobTitle || ""}`;
    return text.toLowerCase().includes(keyword.value.toLowerCase());
  });
});

onMounted(loadCenter);

async function loadCenter() {
  loading.value = true;
  errorMessage.value = "";
  try {
    center.value = await getFollowUpCenter();
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : "求职跟进中心加载失败";
    ElMessage.error(errorMessage.value);
  } finally {
    loading.value = false;
  }
}

async function completeReminder(id: number) {
  try {
    await markReminderDone(id);
    ElMessage.success("提醒已完成");
    await loadCenter();
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : "提醒处理失败");
  }
}

function goAction(action: FrontFollowUpActionInfo) {
  router.push(action.targetPath || "/application");
}

function priorityText(priority: FrontFollowUpApplicationInfo["priority"]) {
  if (priority === "HIGH") return "高优先级";
  if (priority === "NORMAL") return "普通";
  return "低优先级";
}
</script>

<style scoped>
.follow-up-page {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.follow-hero,
.toolbar,
.follow-card,
.summary-card {
  border: 1px solid #e5e7eb;
  border-radius: 18px;
  background: #ffffff;
}

.follow-hero {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 18px;
  padding: 24px;
  background: linear-gradient(135deg, #eff6ff, #ffffff);
}

.follow-hero h1 {
  margin: 6px 0;
  color: #111827;
}

.follow-hero p {
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

.summary-card.warning {
  background: #fff7ed;
  border-color: #fed7aa;
}

.summary-card.danger {
  background: #fef2f2;
  border-color: #fecaca;
}

.summary-card.primary {
  background: #eff6ff;
  border-color: #bfdbfe;
}

.toolbar {
  display: grid;
  grid-template-columns: 220px 1fr;
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

.toolbar input,
.toolbar select {
  padding: 10px 12px;
  border: 1px solid #d1d5db;
  border-radius: 12px;
  font: inherit;
}

.follow-list {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.follow-card {
  display: grid;
  grid-template-columns: 1fr 280px;
  gap: 18px;
  padding: 20px;
}

.follow-card.high {
  border-color: #fecaca;
}

.card-main {
  min-width: 0;
}

.title-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
}

.title-row h3 {
  margin: 0;
  color: #111827;
}

.priority-pill,
.status-pill {
  padding: 4px 9px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 700;
}

.priority-pill {
  background: #fee2e2;
  color: #b91c1c;
}

.status-pill {
  background: #ecfdf5;
  color: #047857;
}

.meta-line,
.time-line,
.reason-line {
  margin: 8px 0;
  color: #6b7280;
}

.reminder-box {
  margin-top: 14px;
  padding: 14px;
  border-radius: 14px;
  background: #f8fafc;
}

.reminder-box h4,
.action-panel h4 {
  margin: 0 0 10px;
  color: #111827;
}

.reminder-row {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 0;
  border-top: 1px solid #e5e7eb;
}

.reminder-row:first-of-type {
  border-top: 0;
}

.reminder-row p {
  margin: 4px 0;
  color: #4b5563;
}

.reminder-row small {
  color: #6b7280;
}

.action-panel {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.action-button {
  display: flex;
  flex-direction: column;
  gap: 5px;
  padding: 12px;
  border: 1px solid #dbeafe;
  border-radius: 14px;
  background: #eff6ff;
  color: #1d4ed8;
  text-align: left;
  cursor: pointer;
}

.action-button.high {
  border-color: #fecaca;
  background: #fef2f2;
  color: #b91c1c;
}

.action-button small {
  color: #4b5563;
  line-height: 1.5;
}

.action-button b {
  margin-top: 4px;
}

@media (max-width: 900px) {
  .summary-grid,
  .toolbar,
  .follow-card {
    grid-template-columns: 1fr;
  }

  .follow-hero {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
