<template>
  <main class="page-section action-page">
    <section class="action-hero">
      <div>
        <p class="eyebrow">Agent Action Center</p>
        <h1>Agent 行动确认中心</h1>
        <p>这里集中展示 Agent 建议你确认和推进的行动。V1 只记录确认状态，不会自动修改原业务数据。</p>
      </div>
      <button class="primary-button large" type="button" :disabled="loading" @click="loadActions">
        {{ loading ? "刷新中..." : "刷新行动项" }}
      </button>
    </section>

    <p v-if="errorMessage" class="form-error">{{ errorMessage }}</p>
    <p v-if="loading" class="empty-state">正在整理待确认行动...</p>
    <el-empty v-else-if="actions.length === 0" description="暂无待确认行动" />

    <section v-else class="action-list">
      <article v-for="item in actions" :key="item.id" class="action-card">
        <div>
          <div class="title-row">
            <el-tag :type="priorityTagType(item.priority)">
              {{ priorityText(item.priority) }}
            </el-tag>
            <span class="type-pill">{{ sourceText(item.sourceType) }}</span>
          </div>
          <h3>{{ item.actionTitle }}</h3>
          <p>{{ item.actionDesc || "暂无说明" }}</p>
          <div class="meta-line">
            <span>状态：{{ statusText(item.actionStatus) }}</span>
            <span v-if="item.snoozeUntil">稍后：{{ item.snoozeUntil }}</span>
            <span v-if="item.createTime">创建：{{ item.createTime }}</span>
          </div>
        </div>

        <div class="card-actions">
          <button class="primary-button" type="button" @click="goTarget(item)">
            去处理
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

    <el-dialog v-model="snoozeDialogVisible" title="稍后处理" width="420px">
      <el-form label-position="top">
        <el-form-item label="重新提醒时间">
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
        <el-button type="primary" @click="submitSnooze">确认稍后</el-button>
      </template>
    </el-dialog>
  </main>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import { useRouter } from "vue-router";
import { ElMessage, ElMessageBox } from "element-plus";
import {
  ignoreAgentAction,
  listPendingAgentActions,
  markAgentActionDone,
  snoozeAgentAction
} from "../api/agentActionCenter";
import type { AgentActionItemInfo } from "../api/types";

const router = useRouter();
const actions = ref<AgentActionItemInfo[]>([]);
const loading = ref(false);
const errorMessage = ref("");
const snoozeDialogVisible = ref(false);
const currentAction = ref<AgentActionItemInfo | null>(null);
const snoozeForm = reactive<{
  snoozeUntil: string | Date | undefined;
  note: string;
}>({
  snoozeUntil: "",
  note: ""
});

onMounted(loadActions);

async function loadActions() {
  loading.value = true;
  errorMessage.value = "";
  try {
    actions.value = await listPendingAgentActions(50);
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : "行动项加载失败";
    ElMessage.error(errorMessage.value);
  } finally {
    loading.value = false;
  }
}

function goTarget(item: AgentActionItemInfo) {
  router.push(item.targetPath || "/agent-inbox");
}

async function markDone(item: AgentActionItemInfo) {
  await markAgentActionDone(item.id);
  ElMessage.success("已标记完成");
  await loadActions();
}

async function ignoreItem(item: AgentActionItemInfo) {
  await ElMessageBox.confirm("忽略后这条行动不会再出现在待确认列表，确认忽略吗？", "确认忽略", {
    type: "warning",
    confirmButtonText: "确认忽略",
    cancelButtonText: "取消"
  });
  await ignoreAgentAction(item.id);
  ElMessage.success("已忽略");
  await loadActions();
}

function openSnoozeDialog(item: AgentActionItemInfo) {
  currentAction.value = item;
  snoozeForm.snoozeUntil = defaultSnoozeTime();
  snoozeForm.note = "";
  snoozeDialogVisible.value = true;
}

async function submitSnooze() {
  if (!currentAction.value) return;
  const snoozeUntil = normalizeDateTime(snoozeForm.snoozeUntil);
  if (!snoozeUntil) {
    ElMessage.warning("请选择稍后处理时间");
    return;
  }
  await snoozeAgentAction(currentAction.value.id, snoozeUntil, snoozeForm.note);
  ElMessage.success("已设置稍后处理");
  snoozeDialogVisible.value = false;
  await loadActions();
}

function defaultSnoozeTime() {
  const date = new Date();
  date.setHours(date.getHours() + 2);
  return date;
}

function normalizeDateTime(value?: string | Date) {
  if (!value) return "";
  if (value instanceof Date) {
    const pad = (num: number) => String(num).padStart(2, "0");
    return `${value.getFullYear()}-${pad(value.getMonth() + 1)}-${pad(value.getDate())} ${pad(value.getHours())}:${pad(value.getMinutes())}:${pad(value.getSeconds())}`;
  }
  return value;
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

function sourceText(sourceType: string) {
  if (sourceType === "DAILY_REPORT") return "AI 日报";
  if (sourceType === "HR_REPLY") return "HR 回复";
  if (sourceType === "FOLLOW_UP_AGENT") return "跟进 Agent";
  return sourceType;
}

function statusText(status: string) {
  if (status === "SNOOZED") return "稍后处理";
  if (status === "DONE") return "已完成";
  if (status === "IGNORED") return "已忽略";
  return "待确认";
}
</script>

<style scoped>
.action-page {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.action-hero,
.action-card {
  border: 1px solid #e5e7eb;
  border-radius: 16px;
  background: #ffffff;
}

.action-hero {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 18px;
  padding: 24px;
  background: linear-gradient(135deg, #eef2ff, #ffffff);
}

.action-hero h1 {
  margin: 6px 0;
  color: #111827;
}

.action-hero p {
  margin: 0;
  color: #6b7280;
}

.action-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.action-card {
  display: grid;
  grid-template-columns: 1fr auto;
  align-items: center;
  gap: 16px;
  padding: 18px;
}

.title-row,
.meta-line {
  display: flex;
  flex-wrap: wrap;
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

.action-card h3 {
  margin: 10px 0 6px;
  color: #111827;
}

.action-card p {
  margin: 0;
  color: #4b5563;
  line-height: 1.6;
}

.meta-line {
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
  .action-hero,
  .action-card {
    grid-template-columns: 1fr;
  }

  .action-hero {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
