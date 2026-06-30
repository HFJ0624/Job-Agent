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

    <section class="daily-report-panel">
      <div class="panel-header">
        <div>
          <p class="eyebrow">Daily Report</p>
          <h2>最近 Agent 日报</h2>
        </div>
        <button class="secondary-button" type="button" :disabled="reportLoading" @click="generateDailyReport">
          {{ reportLoading ? "生成中..." : "生成今日日报" }}
        </button>
      </div>

      <p v-if="reportErrorMessage" class="form-error">{{ reportErrorMessage }}</p>
      <p v-if="reportLoading && dailyReports.length === 0" class="empty-state">正在整理最近日报...</p>
      <el-empty v-else-if="dailyReports.length === 0" description="暂无 Agent 日报" />

      <div v-else class="daily-report-list">
        <article v-for="report in dailyReports" :key="report.id" class="daily-report-card">
          <div>
            <div class="report-title-row">
              <h3>{{ report.reportTitle }}</h3>
              <el-tag :type="emailStatusTagType(report.emailStatus)">
                {{ emailStatusText(report.emailStatus) }}
              </el-tag>
            </div>
            <p class="report-summary">{{ report.summaryText || "暂无摘要" }}</p>
            <div class="meta-line">
              <span>日期：{{ report.reportDate }}</span>
              <span>待办：{{ report.inboxTotalCount }}</span>
              <span>高优先级：{{ report.highPriorityCount }}</span>
              <span>已到期：{{ report.dueCount }}</span>
            </div>
            <p v-if="report.emailError" class="report-error">邮件说明：{{ report.emailError }}</p>
          </div>
          <pre class="report-content">{{ report.contentText }}</pre>
        </article>
      </div>
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

        <div class="card-actions">
          <button class="primary-button" type="button" @click="goTarget(item)">
            {{ item.actionText || "去处理" }}
          </button>
          <button class="secondary-button" type="button" @click="markDone(item)">
            完成
          </button>
          <button class="secondary-button" type="button" @click="openSnoozeDialog(item)">
            稍后
          </button>
          <button class="text-button danger" type="button" @click="ignoreItem(item)">
            忽略
          </button>
        </div>
      </article>
    </section>

    <el-dialog v-model="snoozeDialogVisible" title="稍后提醒" width="420px">
      <el-form label-position="top">
        <el-form-item label="稍后提醒时间">
          <el-date-picker
            v-model="snoozeForm.snoozeUntil"
            type="datetime"
            placeholder="选择重新显示时间"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="备注">
          <el-input
            v-model="snoozeForm.note"
            type="textarea"
            :rows="2"
            placeholder="可选，例如：下午再处理"
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="snoozeDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitSnooze">确认稍后提醒</el-button>
      </template>
    </el-dialog>
  </main>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { useRouter } from "vue-router";
import { ElMessage, ElMessageBox } from "element-plus";
import {
  getTodayAgentInbox,
  ignoreAgentInboxItem,
  markAgentInboxItemDone,
  snoozeAgentInboxItem
} from "../api/agentInbox";
import {
  generateTodayAgentDailyReport,
  listRecentAgentDailyReports
} from "../api/agentDailyReport";
import type { AgentDailyReportInfo, AgentInboxInfo, AgentInboxItemInfo } from "../api/types";

const router = useRouter();
const inbox = ref<AgentInboxInfo | null>(null);
const dailyReports = ref<AgentDailyReportInfo[]>([]);
const loading = ref(false);
const reportLoading = ref(false);
const errorMessage = ref("");
const reportErrorMessage = ref("");
const priorityFilter = ref("");
const typeFilter = ref("");
const snoozeDialogVisible = ref(false);
const currentSnoozeItem = ref<AgentInboxItemInfo | null>(null);
const snoozeForm = reactive<{
  snoozeUntil: string | Date | undefined;
  note: string;
}>({
  snoozeUntil: "",
  note: ""
});

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

onMounted(() => {
  loadInbox();
  loadDailyReports();
});

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

/**
 * 加载最近 Agent 日报。
 *
 * 步骤：
 * 1. 调用后端最近日报接口，只取最近 7 条，避免 Inbox 页面过重。
 * 2. 失败时只提示日报区域错误，不影响上方待办列表继续使用。
 */
async function loadDailyReports() {
  reportLoading.value = true;
  reportErrorMessage.value = "";
  try {
    dailyReports.value = await listRecentAgentDailyReports(7);
  } catch (error) {
    reportErrorMessage.value = error instanceof Error ? error.message : "Agent 日报加载失败";
  } finally {
    reportLoading.value = false;
  }
}

/**
 * 手动生成今日日报。
 *
 * 步骤：
 * 1. 调用后端生成接口，第一版默认同时发送邮件。
 * 2. 生成后重新加载日报列表，让用户能立即看到落库结果。
 */
async function generateDailyReport() {
  reportLoading.value = true;
  reportErrorMessage.value = "";
  try {
    const report = await generateTodayAgentDailyReport(true);
    ElMessage.success(emailStatusText(report.emailStatus) === "发送失败"
      ? "日报已生成，但邮件发送失败"
      : "今日日报已生成");
    await loadDailyReports();
  } catch (error) {
    reportErrorMessage.value = error instanceof Error ? error.message : "Agent 日报生成失败";
    ElMessage.error(reportErrorMessage.value);
  } finally {
    reportLoading.value = false;
  }
}

function goTarget(item: AgentInboxItemInfo) {
  router.push(item.targetPath || "/follow-up");
}

/**
 * 标记待办完成。
 *
 * 说明：
 * 第二版只更新 Inbox 处理记录，不联动修改原始业务表。
 */
async function markDone(item: AgentInboxItemInfo) {
  const payload = await buildDonePayload(item);
  await markAgentInboxItemDone(item.itemKey, payload);
  ElMessage.success(doneSuccessText(item));
  await loadInbox();
}

/**
 * 忽略待办。
 */
async function ignoreItem(item: AgentInboxItemInfo) {
  await ElMessageBox.confirm(
    "忽略后这条待办不会再出现在 Agent Inbox 中，确认忽略吗？",
    "确认忽略",
    {
      type: "warning",
      confirmButtonText: "确认忽略",
      cancelButtonText: "取消"
    }
  );

  await ignoreAgentInboxItem(item.itemKey);
  ElMessage.success("已忽略该待办");
  await loadInbox();
}

/**
 * 打开稍后提醒弹窗。
 */
function openSnoozeDialog(item: AgentInboxItemInfo) {
  currentSnoozeItem.value = item;
  snoozeForm.snoozeUntil = defaultSnoozeTime();
  snoozeForm.note = "";
  snoozeDialogVisible.value = true;
}

/**
 * 提交稍后提醒。
 */
async function submitSnooze() {
  if (!currentSnoozeItem.value) {
    return;
  }

  const snoozeUntil = normalizeDateTime(snoozeForm.snoozeUntil);
  if (!snoozeUntil) {
    ElMessage.warning("请选择稍后提醒时间");
    return;
  }

  await snoozeAgentInboxItem(currentSnoozeItem.value.itemKey, snoozeUntil, snoozeForm.note);
  ElMessage.success("已设置稍后提醒");
  snoozeDialogVisible.value = false;
  await loadInbox();
}

function defaultSnoozeTime() {
  const date = new Date();
  date.setHours(date.getHours() + 2);
  return date;
}

function normalizeDateTime(value?: string | Date) {
  if (!value) {
    return "";
  }
  if (value instanceof Date) {
    const pad = (num: number) => String(num).padStart(2, "0");
    return `${value.getFullYear()}-${pad(value.getMonth() + 1)}-${pad(value.getDate())} ${pad(value.getHours())}:${pad(value.getMinutes())}:${pad(value.getSeconds())}`;
  }
  return value;
}

/**
 * 构造完成动作入参。
 *
 * 说明：
 * 1. 提醒和学习计划由后端按类型直接联动完成。
 * 2. 错题需要用户选择掌握状态，避免误把不熟的题直接标记为已掌握。
 */
async function buildDonePayload(item: AgentInboxItemInfo) {
  if (item.itemType !== "WRONG_QUESTION_REVIEW") {
    return undefined;
  }

  const result = await ElMessageBox.confirm(
    "这道错题你现在掌握了吗？选择“已掌握”会同步更新错题本状态，选择“继续复习”会进入复习中。",
    "错题掌握状态",
    {
      type: "info",
      confirmButtonText: "已掌握",
      cancelButtonText: "继续复习",
      distinguishCancelAndClose: true
    }
  ).then(
    () => "MASTERED",
    (action) => {
      if (action === "cancel") {
        return "REVIEWING";
      }
      throw new Error("cancelled");
    }
  );

  return {
    businessStatus: result
  };
}

function doneSuccessText(item: AgentInboxItemInfo) {
  if (item.itemType === "REMINDER") {
    return "提醒已完成";
  }
  if (item.itemType === "LEARNING_PLAN") {
    return "学习计划任务已完成";
  }
  if (item.itemType === "WRONG_QUESTION_REVIEW") {
    return "错题状态已更新";
  }
  return "已从 Agent 待办中移除";
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

function emailStatusText(status: string) {
  if (status === "SENT") return "已发送";
  if (status === "FAILED") return "发送失败";
  if (status === "SKIPPED") return "未发送";
  return "待发送";
}

function emailStatusTagType(status: string) {
  if (status === "SENT") return "success";
  if (status === "FAILED") return "danger";
  if (status === "SKIPPED") return "info";
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
.daily-report-panel,
.daily-report-card,
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

.daily-report-panel {
  padding: 18px;
}

.panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 14px;
  margin-bottom: 14px;
}

.panel-header h2 {
  margin: 4px 0 0;
  color: #111827;
  font-size: 20px;
}

.daily-report-list {
  display: grid;
  grid-template-columns: 1fr;
  gap: 12px;
}

.daily-report-card {
  display: grid;
  grid-template-columns: minmax(240px, 0.8fr) minmax(260px, 1.2fr);
  gap: 14px;
  padding: 16px;
}

.report-title-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.report-title-row h3 {
  margin: 0;
  color: #111827;
  font-size: 16px;
}

.report-summary {
  margin: 10px 0 0;
  color: #4b5563;
  line-height: 1.6;
}

.report-error {
  margin: 10px 0 0;
  color: #dc2626;
  font-size: 13px;
}

.report-content {
  max-height: 220px;
  margin: 0;
  padding: 12px;
  overflow: auto;
  border-radius: 12px;
  background: #f8fafc;
  color: #334155;
  font-family: inherit;
  font-size: 13px;
  line-height: 1.7;
  white-space: pre-wrap;
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

.card-actions {
  display: grid;
  grid-template-columns: 1fr;
  gap: 8px;
  min-width: 112px;
}

.card-actions .danger {
  color: #dc2626;
}

@media (max-width: 900px) {
  .summary-grid,
  .toolbar,
  .daily-report-card,
  .inbox-card {
    grid-template-columns: 1fr;
  }

  .inbox-hero {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
