<template>
  <main class="action-page">
    <section class="page-header">
      <div>
        <p class="eyebrow">Agent Actions</p>
        <h1>Agent 行动项</h1>
        <p>集中查看模型生成的行动项、执行状态、失败原因和关联工作流任务。</p>
      </div>
      <div class="header-actions">
        <el-button type="primary" :loading="loading" @click="loadActions">刷新</el-button>
      </div>
    </section>

    <section class="filter-card">
      <el-form :model="query" label-width="92px" class="filter-form">
        <el-form-item label="用户ID">
          <el-input v-model.trim="query.userId" clearable placeholder="userId" />
        </el-form-item>
        <el-form-item label="来源">
          <el-select v-model="query.sourceType" clearable placeholder="全部">
            <el-option label="DAILY_REPORT" value="DAILY_REPORT" />
            <el-option label="HR_REPLY" value="HR_REPLY" />
            <el-option label="FOLLOW_UP_AGENT" value="FOLLOW_UP_AGENT" />
          </el-select>
        </el-form-item>
        <el-form-item label="动作类型">
          <el-select v-model="query.actionType" clearable placeholder="全部">
            <el-option v-for="item in actionTypes" :key="item" :label="item" :value="item" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.actionStatus" clearable placeholder="全部">
            <el-option v-for="item in actionStatuses" :key="item" :label="item" :value="item" />
          </el-select>
        </el-form-item>
        <el-form-item label="工作流">
          <el-select v-model="query.hasWorkflowTask" clearable placeholder="全部">
            <el-option label="有关联任务" value="true" />
            <el-option label="无关联任务" value="false" />
          </el-select>
        </el-form-item>
        <el-form-item label="任务ID">
          <el-input v-model.trim="query.workflowTaskId" clearable placeholder="workflowTaskId" />
        </el-form-item>
        <el-form-item label="失败项">
          <el-switch v-model="query.failedOnly" active-text="只看失败" />
        </el-form-item>
        <el-form-item label="关键词">
          <el-input v-model.trim="query.keyword" clearable placeholder="标题 / 描述 / payload" />
        </el-form-item>
        <el-form-item class="filter-actions">
          <el-button type="primary" @click="searchActions">查询</el-button>
          <el-button @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>
    </section>

    <section class="table-card">
      <el-table v-loading="loading" :data="actions" border stripe>
        <el-table-column prop="id" label="ID" width="80" fixed />
        <el-table-column prop="userId" label="用户ID" width="100" />
        <el-table-column prop="sourceType" label="来源" width="140" />
        <el-table-column prop="actionType" label="动作类型" min-width="190" show-overflow-tooltip />
        <el-table-column prop="actionStatus" label="状态" width="120">
          <template #default="{ row }">
            <el-tag :type="actionStatusTag(row.actionStatus)" effect="plain">{{ row.actionStatus }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="priority" label="优先级" width="100" />
        <el-table-column prop="actionTitle" label="标题" min-width="220" show-overflow-tooltip />
        <el-table-column prop="bizType" label="业务类型" min-width="130" />
        <el-table-column prop="bizId" label="业务ID" width="100" />
        <el-table-column label="工作流" min-width="220">
          <template #default="{ row }">
            <template v-if="row.workflowTaskId">
              <el-button link type="primary" @click="goWorkflow(row)">{{ row.workflowTaskNo || row.workflowTaskId }}</el-button>
              <el-tag :type="workflowStatusTag(row.workflowTaskStatus)" effect="plain">
                {{ row.workflowTaskStatus || "UNKNOWN" }}
              </el-tag>
            </template>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="进度" width="150">
          <template #default="{ row }">
            <el-progress v-if="row.workflowTaskId" :percentage="row.workflowTaskProgress || 0" />
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column prop="executeError" label="执行错误" min-width="220" show-overflow-tooltip />
        <el-table-column prop="createTime" label="创建时间" width="170" />
        <el-table-column label="操作" fixed="right" width="150">
          <template #default="{ row }">
            <el-button link type="primary" @click="openPayload(row)">Payload</el-button>
            <el-button link type="danger" @click="openError(row)">错误</el-button>
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

    <el-dialog v-model="detailVisible" :title="detailTitle" width="760px">
      <pre class="json-box">{{ detailText }}</pre>
    </el-dialog>
  </main>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import { useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import { pageAgentActions } from "../../api/agentAction";
import type { AgentActionItemInfo, AgentActionItemQuery } from "../../api/types";

const router = useRouter();
const actionTypes = [
  "MANUAL_CONFIRM",
  "REMINDER_CREATE",
  "REMINDER_DONE",
  "LEARNING_PLAN_DONE",
  "WRONG_QUESTION_REVIEWED",
  "WRONG_QUESTION_MASTERED",
  "WORKFLOW_TASK_CREATE"
];
const actionStatuses = ["PENDING", "DONE", "FAILED", "IGNORED", "SNOOZED"];

const query = reactive<AgentActionItemQuery>({
  pageNum: 1,
  pageSize: 10,
  userId: "",
  sourceType: "",
  actionType: "",
  actionStatus: "",
  failedOnly: false,
  hasWorkflowTask: "",
  workflowTaskId: "",
  keyword: ""
});

const actions = ref<AgentActionItemInfo[]>([]);
const total = ref(0);
const loading = ref(false);
const detailVisible = ref(false);
const detailTitle = ref("");
const detailText = ref("");

onMounted(loadActions);

async function loadActions() {
  loading.value = true;
  try {
    const page = await pageAgentActions(normalizeQuery());
    actions.value = page.records || [];
    total.value = page.total || 0;
    query.pageNum = page.current || query.pageNum;
    query.pageSize = page.size || query.pageSize;
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : "行动项加载失败");
  } finally {
    loading.value = false;
  }
}

function normalizeQuery() {
  return {
    ...query,
    hasWorkflowTask: query.hasWorkflowTask === "" ? "" : query.hasWorkflowTask === "true"
  };
}

function searchActions() {
  query.pageNum = 1;
  loadActions();
}

function resetQuery() {
  Object.assign(query, {
    pageNum: 1,
    pageSize: 10,
    userId: "",
    sourceType: "",
    actionType: "",
    actionStatus: "",
    failedOnly: false,
    hasWorkflowTask: "",
    workflowTaskId: "",
    keyword: ""
  });
  loadActions();
}

function handleCurrentChange(page: number) {
  query.pageNum = page;
  loadActions();
}

function handleSizeChange(size: number) {
  query.pageSize = size;
  query.pageNum = 1;
  loadActions();
}

function openPayload(row: AgentActionItemInfo) {
  detailTitle.value = "行动参数";
  detailText.value = formatJson(row.actionPayload || "{}");
  detailVisible.value = true;
}

function openError(row: AgentActionItemInfo) {
  detailTitle.value = "错误详情";
  detailText.value = [row.executeError, row.workflowTaskError].filter(Boolean).join("\n\n") || "暂无错误";
  detailVisible.value = true;
}

function goWorkflow(row: AgentActionItemInfo) {
  router.push({
    path: "/agent/workflow-tasks",
    query: {
      taskNo: row.workflowTaskNo || "",
      userId: row.userId || "",
      bizId: row.bizId || ""
    }
  });
}

function formatJson(value: string) {
  try {
    return JSON.stringify(JSON.parse(value), null, 2);
  } catch {
    return value || "{}";
  }
}

function actionStatusTag(status: string) {
  if (status === "DONE") return "success";
  if (status === "FAILED") return "danger";
  if (status === "SNOOZED") return "warning";
  return "info";
}

function workflowStatusTag(status?: string) {
  if (status === "SUCCESS") return "success";
  if (status === "FAILED_RETRYABLE" || status === "FAILED_FINAL") return "danger";
  if (status === "RUNNING") return "warning";
  return "info";
}
</script>

<style scoped>
.action-page {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.page-header,
.filter-card,
.table-card {
  padding: 18px;
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 8px 24px rgb(15 23 42 / 6%);
}

.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.eyebrow {
  margin: 0 0 6px;
  font-size: 12px;
  font-weight: 700;
  color: #2563eb;
  text-transform: uppercase;
}

.page-header h1 {
  margin: 0;
  font-size: 24px;
  color: #111827;
}

.page-header p:last-child {
  margin: 8px 0 0;
  color: #64748b;
}

.filter-form {
  display: grid;
  grid-template-columns: repeat(4, minmax(180px, 1fr));
  gap: 8px 12px;
}

.filter-actions {
  align-items: flex-end;
}

.pagination-row {
  margin-top: 16px;
  justify-content: flex-end;
}

.json-box {
  max-height: 520px;
  overflow: auto;
  padding: 12px;
  border-radius: 8px;
  background: #0f172a;
  color: #e2e8f0;
  white-space: pre-wrap;
}

@media (max-width: 1100px) {
  .filter-form {
    grid-template-columns: repeat(2, minmax(180px, 1fr));
  }
}

@media (max-width: 720px) {
  .page-header {
    align-items: flex-start;
    flex-direction: column;
  }

  .filter-form {
    grid-template-columns: 1fr;
  }
}
</style>
