<template>
  <section class="reminder-panel">
    <div class="panel-header">
      <div>
        <h3>求职提醒</h3>
        <p>面试、HR 跟进和待处理事项会显示在这里。</p>
      </div>

      <el-button size="small" @click="loadReminders">
        刷新
      </el-button>
    </div>

    <div class="stats-grid">
      <div class="stat-card">
        <span>待处理</span>
        <strong>{{ stats.pendingCount }}</strong>
      </div>

      <div class="stat-card danger">
        <span>已到期</span>
        <strong>{{ stats.dueCount }}</strong>
      </div>

      <div class="stat-card primary">
        <span>今日提醒</span>
        <strong>{{ stats.todayCount }}</strong>
      </div>

      <div class="stat-card warning">
        <span>面试提醒</span>
        <strong>{{ stats.interviewCount }}</strong>
      </div>
    </div>

    <div v-loading="loading" class="reminder-list">
      <el-empty
        v-if="!loading && reminders.length === 0"
        description="暂无待处理提醒"
      />

      <article
        v-for="item in reminders"
        :key="item.id"
        class="reminder-card"
        :class="{ overdue: item.overdue }"
      >
        <div class="reminder-main">
          <div class="title-row">
            <h4>{{ item.reminderTitle }}</h4>

            <el-tag
              size="small"
              :type="item.overdue ? 'danger' : reminderTypeTag(item.reminderType)"
            >
              {{ item.reminderTypeDesc || reminderTypeText(item.reminderType) }}
            </el-tag>

            <el-tag v-if="item.overdue" size="small" type="danger">
              已到期
            </el-tag>
          </div>

          <p class="meta">
            <span>{{ item.companyName || "未知公司" }}</span>
            <span>{{ item.jobTitle || "未知岗位" }}</span>
          </p>

          <p class="content">
            {{ item.reminderContent || "暂无提醒内容" }}
          </p>

          <p class="time">
            提醒时间：{{ formatTime(item.remindTime) }}
            <span v-if="item.eventTime">
              ｜事件时间：{{ formatTime(item.eventTime) }}
            </span>
          </p>
        </div>

        <div class="actions">
          <el-button size="small" type="success" plain @click="done(item)">
            完成
          </el-button>

          <el-button size="small" type="primary" plain @click="openPostpone(item)">
            延期
          </el-button>

          <el-button size="small" type="danger" plain @click="cancel(item)">
            取消
          </el-button>
        </div>
      </article>
    </div>

    <el-dialog v-model="postponeVisible" title="延期提醒" width="420px">
      <el-form label-position="top">
        <el-form-item label="新的提醒时间">
          <el-date-picker
            v-model="postponeForm.remindTime"
            type="datetime"
            style="width: 100%"
            placeholder="选择新的提醒时间"
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="postponeVisible = false">取消</el-button>
        <el-button type="primary" @click="submitPostpone">保存</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import {
  cancelReminder,
  getReminderStats,
  markReminderDone,
  pageReminders,
  postponeReminder
} from "../api/reminder";
import type { JobReminderInfo, ReminderStatsInfo } from "../api/types";

/**
 * 加载状态。
 */
const loading = ref(false);

/**
 * 提醒列表。
 */
const reminders = ref<JobReminderInfo[]>([]);

/**
 * 提醒统计。
 */
const stats = reactive<ReminderStatsInfo>({
  pendingCount: 0,
  dueCount: 0,
  todayCount: 0,
  interviewCount: 0,
  followUpCount: 0,
  unreadCount: 0
});

/**
 * 延期弹窗。
 */
const postponeVisible = ref(false);

/**
 * 延期表单。
 */
const postponeForm = reactive<{
  id: number;
  remindTime: string | Date | undefined;
}>({
  id: 0,
  remindTime: ""
});

/**
 * 加载提醒统计。
 */
async function loadStats() {
  const data = await getReminderStats();

  stats.pendingCount = data.pendingCount || 0;
  stats.dueCount = data.dueCount || 0;
  stats.todayCount = data.todayCount || 0;
  stats.interviewCount = data.interviewCount || 0;
  stats.followUpCount = data.followUpCount || 0;
  stats.unreadCount = data.unreadCount || 0;
}

/**
 * 加载待处理提醒。
 */
async function loadReminders() {
  loading.value = true;

  try {
    const data = await pageReminders({
      pageNo: 1,
      pageSize: 10,
      reminderStatus: "PENDING"
    });

    reminders.value = data.records || [];

    await loadStats();
  } finally {
    loading.value = false;
  }
}

/**
 * 标记完成。
 */
async function done(item: JobReminderInfo) {
  await markReminderDone(item.id);

  ElMessage.success("已标记完成");

  await loadReminders();
}

/**
 * 取消提醒。
 */
async function cancel(item: JobReminderInfo) {
  await ElMessageBox.confirm(
    "确认取消这个提醒吗？",
    "提示",
    {
      type: "warning"
    }
  );

  await cancelReminder(item.id, "用户手动取消");

  ElMessage.success("已取消提醒");

  await loadReminders();
}

/**
 * 打开延期弹窗。
 */
function openPostpone(item: JobReminderInfo) {
  postponeForm.id = item.id;
  postponeForm.remindTime = item.remindTime || "";

  postponeVisible.value = true;
}

/**
 * 提交延期。
 */
async function submitPostpone() {
  if (!postponeForm.remindTime) {
    ElMessage.warning("请选择新的提醒时间");
    return;
  }

  await postponeReminder(postponeForm.id, {
    remindTime: normalizeDateTime(postponeForm.remindTime)
  });

  ElMessage.success("提醒已延期");

  postponeVisible.value = false;

  await loadReminders();
}

/**
 * 提醒类型标签。
 */
function reminderTypeTag(type: string) {
  if (type === "INTERVIEW") {
    return "warning";
  }

  if (type === "FOLLOW_UP") {
    return "primary";
  }

  return "info";
}

/**
 * 提醒类型中文。
 */
function reminderTypeText(type: string) {
  const map: Record<string, string> = {
    INTERVIEW: "面试提醒",
    FOLLOW_UP: "跟进提醒",
    CUSTOM: "自定义提醒"
  };

  return map[type] || type;
}

/**
 * 格式化时间。
 */
function formatTime(value?: string | Date) {
  if (!value) {
    return "";
  }

  if (value instanceof Date) {
    return formatDate(value);
  }

  return value.replace("T", " ").slice(0, 16);
}

/**
 * Date 转 yyyy-MM-dd HH:mm:ss。
 */
function formatDate(date: Date) {
  const pad = (num: number) => String(num).padStart(2, "0");

  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(
    date.getHours()
  )}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`;
}

/**
 * 提交后端前统一时间格式。
 */
function normalizeDateTime(value?: string | Date) {
  if (!value) {
    return "";
  }

  if (value instanceof Date) {
    return formatDate(value);
  }

  return value;
}

onMounted(() => {
  loadReminders();
});
</script>

<style scoped>
.reminder-panel {
  padding: 18px;
  margin-bottom: 16px;
  border: 1px solid #e3eaf0;
  border-radius: 16px;
  background: #ffffff;
  box-shadow: 0 8px 24px rgba(15, 23, 42, 0.04);
}

.panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 14px;
}

.panel-header h3 {
  margin: 0;
  font-size: 18px;
  color: #101828;
}

.panel-header p {
  margin: 4px 0 0;
  font-size: 13px;
  color: #667085;
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 10px;
  margin-bottom: 14px;
}

.stat-card {
  padding: 12px;
  border-radius: 12px;
  background: #f8fafc;
  border: 1px solid #e5e7eb;
}

.stat-card span {
  display: block;
  margin-bottom: 4px;
  font-size: 12px;
  color: #667085;
}

.stat-card strong {
  font-size: 22px;
  color: #101828;
}

.stat-card.danger {
  background: #fef2f2;
  border-color: #fecaca;
}

.stat-card.primary {
  background: #eff6ff;
  border-color: #bfdbfe;
}

.stat-card.warning {
  background: #fff7ed;
  border-color: #fed7aa;
}

.reminder-list {
  min-height: 120px;
}

.reminder-card {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  padding: 14px;
  margin-bottom: 10px;
  border-radius: 12px;
  border: 1px solid #e5e7eb;
  background: #ffffff;
}

.reminder-card.overdue {
  border-color: #fecaca;
  background: #fff7f7;
}

.reminder-main {
  flex: 1;
  min-width: 0;
}

.title-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.title-row h4 {
  margin: 0;
  font-size: 15px;
  color: #101828;
}

.meta,
.content,
.time {
  margin: 5px 0;
  font-size: 13px;
  color: #667085;
}

.meta {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.actions {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.actions :deep(.el-button) {
  margin-left: 0;
}

@media (max-width: 900px) {
  .stats-grid {
    grid-template-columns: repeat(2, 1fr);
  }

  .reminder-card {
    flex-direction: column;
  }

  .actions {
    flex-direction: row;
  }
}
</style>