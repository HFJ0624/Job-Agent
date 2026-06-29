<template>
  <main class="workflow-page">
    <section class="page-header">
      <div>
        <p class="eyebrow">Workflow Queue</p>
        <h1>工作流任务</h1>
        <p>查看异步长任务状态、进度、阶段日志，并支持失败任务手动重试或取消。</p>
      </div>
      <div class="header-actions">
        <el-button type="primary" :loading="loading" @click="loadTasks">刷新</el-button>
      </div>
    </section>

    <section class="quick-card">
      <el-button type="primary" :loading="creating === 'ragAll'" @click="createRagAll">
        异步重建全部 RAG
      </el-button>
      <el-input-number v-model="targetUserId" :min="1" controls-position="right" />
      <el-button type="warning" :loading="creating === 'ragUser'" @click="createRagUser">
        异步重建用户 RAG
      </el-button>
      <el-input-number v-model="targetDatasetId" :min="1" controls-position="right" />
      <el-button type="success" :loading="creating === 'evalDataset'" @click="createEvalDataset">
        异步执行 Eval 数据集
      </el-button>
    </section>

    <section class="filter-card">
      <el-form :model="query" label-width="86px" class="filter-form">
        <el-form-item label="任务编号">
          <el-input v-model.trim="query.taskNo" clearable placeholder="任务编号" />
        </el-form-item>
        <el-form-item label="任务类型">
          <el-select v-model="query.taskType" clearable placeholder="全部">
            <el-option label="RAG_REBUILD_ALL" value="RAG_REBUILD_ALL" />
            <el-option label="RAG_REBUILD_USER" value="RAG_REBUILD_USER" />
            <el-option label="AGENT_EVAL_RUN_DATASET" value="AGENT_EVAL_RUN_DATASET" />
            <el-option label="INTERVIEW_EMAIL_NOTIFY" value="INTERVIEW_EMAIL_NOTIFY" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" clearable placeholder="全部">
            <el-option v-for="item in statusOptions" :key="item" :label="item" :value="item" />
          </el-select>
        </el-form-item>
        <el-form-item label="业务ID">
          <el-input v-model.trim="query.bizId" clearable placeholder="bizId" />
        </el-form-item>
        <el-form-item label="用户ID">
          <el-input v-model.trim="query.userId" clearable placeholder="userId" />
        </el-form-item>
        <el-form-item class="filter-actions">
          <el-button type="primary" @click="searchTasks">查询</el-button>
          <el-button @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>
    </section>

    <section class="table-card">
      <el-table v-loading="loading" :data="tasks" border stripe>
        <el-table-column prop="id" label="ID" width="80" fixed />
        <el-table-column prop="taskNo" label="任务编号" min-width="230" show-overflow-tooltip />
        <el-table-column prop="taskType" label="任务类型" min-width="210" />
        <el-table-column prop="status" label="状态" width="160">
          <template #default="{ row }">
            <el-tag :type="statusTag(row.status)" effect="plain">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="进度" width="190">
          <template #default="{ row }">
            <el-progress :percentage="row.progressPercent || 0" :status="progressStatus(row.status)" />
          </template>
        </el-table-column>
        <el-table-column prop="currentStep" label="当前阶段" min-width="160" show-overflow-tooltip />
        <el-table-column prop="retryCount" label="重试" width="90">
          <template #default="{ row }">
            {{ row.retryCount || 0 }}/{{ row.maxRetryCount || 0 }}
          </template>
        </el-table-column>
        <el-table-column prop="costTime" label="耗时" width="110">
          <template #default="{ row }">{{ row.costTime || 0 }} ms</template>
        </el-table-column>
        <el-table-column prop="errorMsg" label="失败原因" min-width="220" show-overflow-tooltip />
        <el-table-column prop="createTime" label="创建时间" width="170" />
        <el-table-column label="操作" fixed="right" width="230">
          <template #default="{ row }">
            <el-button link type="primary" @click="openLogs(row)">日志</el-button>
            <el-button link type="success" @click="openResult(row)">结果</el-button>
            <el-button link type="warning" :disabled="!canRetry(row.status)" @click="retryTask(row)">重试</el-button>
            <el-button link type="danger" :disabled="row.status === 'SUCCESS'" @click="cancelTask(row)">取消</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        class="pagination-row"
        background
        layout="total, sizes, prev, pager, next, jumper"
        :current-page="query.pageNum"
        :page-size="query.pageSize"
        :page-sizes="[10, 20, 50, 100]"
        :total="total"
        @current-change="handleCurrentChange"
        @size-change="handleSizeChange"
      />
    </section>

    <el-drawer v-model="logVisible" title="任务阶段日志" size="52%">
      <el-timeline v-if="logs.length > 0">
        <el-timeline-item
          v-for="item in logs"
          :key="item.id"
          :timestamp="item.createTime"
          :type="item.logLevel === 'ERROR' ? 'danger' : item.logLevel === 'WARN' ? 'warning' : 'primary'"
        >
          <strong>{{ item.stepName || "-" }} · {{ item.progressPercent ?? "-" }}%</strong>
          <p>{{ item.logMessage || "-" }}</p>
          <p v-if="item.errorMsg" class="error-text">{{ item.errorMsg }}</p>
        </el-timeline-item>
      </el-timeline>
      <el-empty v-else description="暂无阶段日志" />
    </el-drawer>

    <el-dialog v-model="resultVisible" title="任务结果" width="760px">
      <pre class="json-box">{{ resultText }}</pre>
    </el-dialog>
  </main>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import { useRoute } from "vue-router";
import { ElMessage, ElMessageBox } from "element-plus";
import {
  cancelWorkflowTask,
  createEvalDatasetRunTask,
  createRagRebuildAllTask,
  createRagRebuildUserTask,
  listWorkflowTaskLogs,
  pageWorkflowTasks,
  retryWorkflowTask
} from "../../api/workflow";
import type { WorkflowTaskInfo, WorkflowTaskLogInfo, WorkflowTaskQuery } from "../../api/types";

const route = useRoute();
const statusOptions = ["PENDING", "RUNNING", "SUCCESS", "FAILED_RETRYABLE", "FAILED_FINAL", "CANCELLED"];

const query = reactive<WorkflowTaskQuery>({
  pageNum: 1,
  pageSize: 10,
  taskNo: "",
  taskType: "",
  status: "",
  bizId: "",
  userId: ""
});

const tasks = ref<WorkflowTaskInfo[]>([]);
const logs = ref<WorkflowTaskLogInfo[]>([]);
const total = ref(0);
const loading = ref(false);
const logVisible = ref(false);
const resultVisible = ref(false);
const resultText = ref("{}");
const creating = ref("");
const targetUserId = ref<number | null>(null);
const targetDatasetId = ref<number | null>(null);

onMounted(() => {
  applyRouteQuery();
  loadTasks();
});

function applyRouteQuery() {
  /*
   * 从后台首页跳转过来时，会带 taskType/status 等筛选参数。
   * 这里只接受工作流列表本身已经支持的字段，避免 URL 上的无关参数污染查询。
   */
  query.taskType = toQueryValue(route.query.taskType);
  query.status = toQueryValue(route.query.status);
  query.bizId = toQueryValue(route.query.bizId);
  query.userId = toQueryValue(route.query.userId);
  query.taskNo = toQueryValue(route.query.taskNo);
}

function toQueryValue(value: unknown) {
  if (Array.isArray(value)) {
    return value[0] ? String(value[0]) : "";
  }
  return value ? String(value) : "";
}

async function loadTasks() {
  loading.value = true;
  try {
    const page = await pageWorkflowTasks({ ...query });
    tasks.value = page.records || [];
    total.value = page.total || 0;
    query.pageNum = page.current || query.pageNum;
    query.pageSize = page.size || query.pageSize;
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : "工作流任务加载失败");
  } finally {
    loading.value = false;
  }
}

function searchTasks() {
  query.pageNum = 1;
  loadTasks();
}

function resetQuery() {
  Object.assign(query, { pageNum: 1, pageSize: 10, taskNo: "", taskType: "", status: "", bizId: "", userId: "" });
  loadTasks();
}

async function createRagAll() {
  await createTask("ragAll", () => createRagRebuildAllTask());
}

async function createRagUser() {
  if (!targetUserId.value) {
    ElMessage.warning("请输入用户ID");
    return;
  }
  await createTask("ragUser", () => createRagRebuildUserTask(Number(targetUserId.value)));
}

async function createEvalDataset() {
  if (!targetDatasetId.value) {
    ElMessage.warning("请输入数据集ID");
    return;
  }
  await createTask("evalDataset", () => createEvalDatasetRunTask(Number(targetDatasetId.value)));
}

async function createTask(key: string, action: () => Promise<WorkflowTaskInfo>) {
  creating.value = key;
  try {
    const task = await action();
    ElMessage.success(`任务已创建：${task.taskNo}`);
    await loadTasks();
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : "任务创建失败");
  } finally {
    creating.value = "";
  }
}

async function openLogs(row: WorkflowTaskInfo) {
  logs.value = await listWorkflowTaskLogs(row.id);
  logVisible.value = true;
}

function openResult(row: WorkflowTaskInfo) {
  resultText.value = formatJson(row.resultJson || row.errorMsg || "{}");
  resultVisible.value = true;
}

async function retryTask(row: WorkflowTaskInfo) {
  await retryWorkflowTask(row.id);
  ElMessage.success("任务已重新进入队列");
  await loadTasks();
}

async function cancelTask(row: WorkflowTaskInfo) {
  try {
    await ElMessageBox.confirm("确认取消该任务吗？", "取消任务", { type: "warning" });
  } catch {
    return;
  }
  await cancelWorkflowTask(row.id);
  ElMessage.success("任务已取消");
  await loadTasks();
}

function handleCurrentChange(pageNum: number) {
  query.pageNum = pageNum;
  loadTasks();
}

function handleSizeChange(pageSize: number) {
  query.pageSize = pageSize;
  query.pageNum = 1;
  loadTasks();
}

function canRetry(status: string) {
  return ["FAILED_RETRYABLE", "FAILED_FINAL", "CANCELLED"].includes(status);
}

function statusTag(status: string) {
  if (status === "SUCCESS") return "success";
  if (status === "RUNNING" || status === "PENDING") return "warning";
  if (status === "FAILED_RETRYABLE" || status === "FAILED_FINAL") return "danger";
  return "info";
}

function progressStatus(status: string) {
  if (status === "SUCCESS") return "success";
  if (status === "FAILED_FINAL") return "exception";
  return undefined;
}

function formatJson(value: string) {
  try {
    return JSON.stringify(JSON.parse(value), null, 2);
  } catch {
    return value || "{}";
  }
}
</script>

<style scoped>
.workflow-page {
  display: grid;
  gap: 16px;
}

.page-header,
.quick-card,
.filter-card,
.table-card {
  padding: 20px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #ffffff;
}

.page-header,
.quick-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.quick-card {
  justify-content: flex-start;
  flex-wrap: wrap;
}

.page-header h1 {
  margin: 4px 0;
  color: #111827;
}

.page-header p {
  margin: 0;
  color: #6b7280;
}

.eyebrow {
  color: #2563eb;
  font-size: 13px;
  font-weight: 700;
}

.filter-form {
  display: grid;
  grid-template-columns: repeat(3, minmax(220px, 1fr));
  gap: 12px 16px;
}

.filter-form :deep(.el-form-item) {
  margin-bottom: 0;
}

.filter-actions {
  align-items: flex-end;
}

.pagination-row {
  margin-top: 16px;
  justify-content: flex-end;
}

.json-box {
  max-height: 420px;
  overflow: auto;
  padding: 14px;
  border-radius: 8px;
  background: #0f172a;
  color: #e5e7eb;
  font-size: 13px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
}

.error-text {
  color: #dc2626;
}
</style>
