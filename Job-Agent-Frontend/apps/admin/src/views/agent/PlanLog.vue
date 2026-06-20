<template>
  <main class="admin-page">
    <section class="page-header">
      <div>
        <p class="eyebrow">Agent Planner</p>
        <h1>Agent 执行计划</h1>
        <p>查看用户查询被拆解出的计划、步骤、工具选择和完成条件。</p>
      </div>

      <el-button type="primary" :loading="loading" @click="loadPlans">
        刷新
      </el-button>
    </section>

    <section class="filter-card">
      <el-form :model="query" label-width="90px" class="filter-form">
        <el-row :gutter="12">
          <el-col :span="6">
            <el-form-item label="TraceId">
              <el-input
                v-model.trim="query.traceId"
                placeholder="输入 traceId"
                clearable
              />
            </el-form-item>
          </el-col>

          <el-col :span="6">
            <el-form-item label="用户ID">
              <el-input
                v-model.trim="query.userId"
                placeholder="输入用户ID"
                clearable
              />
            </el-form-item>
          </el-col>

          <el-col :span="6">
            <el-form-item label="会话ID">
              <el-input
                v-model.trim="query.conversationId"
                placeholder="输入会话ID"
                clearable
              />
            </el-form-item>
          </el-col>

          <el-col :span="6">
            <el-form-item label="状态">
              <el-select v-model="query.status" placeholder="全部" clearable>
                <el-option label="已计划" value="PLANNED" />
                <el-option label="需补充" value="NEED_CLARIFICATION" />
                <el-option label="已完成" value="COMPLETED" />
                <el-option label="失败" value="FAILED" />
              </el-select>
            </el-form-item>
          </el-col>

          <el-col :span="6">
            <el-form-item label="意图">
              <el-input
                v-model.trim="query.intentCode"
                placeholder="JOB_MATCH"
                clearable
              />
            </el-form-item>
          </el-col>

          <el-col :span="12">
            <el-form-item label="时间范围">
              <el-date-picker
                v-model="timeRange"
                type="datetimerange"
                start-placeholder="开始时间"
                end-placeholder="结束时间"
                value-format="YYYY-MM-DD HH:mm:ss"
                clearable
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
        </el-row>

        <div class="filter-actions">
          <el-button type="primary" :loading="loading" @click="handleSearch">
            查询
          </el-button>
          <el-button @click="resetSearch">
            重置
          </el-button>
        </div>
      </el-form>
    </section>

    <section class="table-card">
      <el-table
        v-loading="loading"
        :data="plans"
        border
        stripe
        style="width: 100%"
      >
        <el-table-column prop="id" label="ID" width="80" />

        <el-table-column prop="traceId" label="TraceId" min-width="220">
          <template #default="{ row }">
            <el-tooltip :content="row.traceId" placement="top">
              <span class="ellipsis-text">{{ row.traceId }}</span>
            </el-tooltip>
          </template>
        </el-table-column>

        <el-table-column prop="userId" label="用户ID" width="100" />
        <el-table-column prop="conversationId" label="会话ID" width="100" />
        <el-table-column prop="intentCode" label="意图" width="150" />

        <el-table-column prop="planTitle" label="计划" min-width="180">
          <template #default="{ row }">
            <span>{{ row.planTitle || "-" }}</span>
          </template>
        </el-table-column>

        <el-table-column prop="status" label="状态" width="120">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)">
              {{ statusText(row.status) }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column label="缺失参数" width="120">
          <template #default="{ row }">
            <span>{{ missingParamCount(row) }}</span>
          </template>
        </el-table-column>

        <el-table-column prop="createTime" label="创建时间" width="170" />

        <el-table-column label="操作" fixed="right" width="160">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">
              查看详情
            </el-button>
            <el-button link type="warning" @click="openJsonDialog('已抽取参数', row.extractedParamsJson)">
              参数
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-row">
        <el-pagination
          v-model:current-page="query.pageNum"
          v-model:page-size="query.pageSize"
          :total="total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="loadPlans"
          @current-change="loadPlans"
        />
      </div>
    </section>

    <el-dialog
      v-model="detailVisible"
      title="Agent 计划详情"
      width="960px"
    >
      <div v-if="currentPlan" class="detail-grid">
        <div>
          <span>TraceId</span>
          <strong>{{ currentPlan.traceId }}</strong>
        </div>
        <div>
          <span>用户ID</span>
          <strong>{{ currentPlan.userId }}</strong>
        </div>
        <div>
          <span>会话ID</span>
          <strong>{{ currentPlan.conversationId || "-" }}</strong>
        </div>
        <div>
          <span>意图</span>
          <strong>{{ currentPlan.intentCode || "-" }}</strong>
        </div>
        <div>
          <span>状态</span>
          <strong>{{ statusText(currentPlan.status) }}</strong>
        </div>
        <div>
          <span>创建时间</span>
          <strong>{{ currentPlan.createTime || "-" }}</strong>
        </div>
      </div>

      <el-divider />

      <h3>用户目标</h3>
      <p class="text-block">{{ currentPlan?.userGoal || "-" }}</p>

      <h3>计划摘要</h3>
      <p class="text-block">{{ currentPlan?.planSummary || "-" }}</p>

      <div class="json-actions">
        <el-button @click="openJsonDialog('必要参数', currentPlan?.requiredParamsJson)">
          必要参数
        </el-button>
        <el-button @click="openJsonDialog('已抽取参数', currentPlan?.extractedParamsJson)">
          已抽取参数
        </el-button>
        <el-button @click="openJsonDialog('缺失参数', currentPlan?.missingParamsJson)">
          缺失参数
        </el-button>
      </div>

      <h3>计划步骤</h3>
      <el-table
        :data="currentPlan?.steps || []"
        border
        stripe
        style="width: 100%"
      >
        <el-table-column prop="stepNo" label="序号" width="70" />
        <el-table-column prop="stepName" label="步骤" min-width="150" />
        <el-table-column prop="stepGoal" label="目标" min-width="210" />
        <el-table-column prop="toolName" label="建议工具" min-width="190">
          <template #default="{ row }">
            <span>{{ row.toolName || "-" }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="stepStatusType(row.status)">
              {{ stepStatusText(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="completionCriteria" label="完成条件" min-width="220" />
        <el-table-column label="输入约束" width="110">
          <template #default="{ row }">
            <el-button
              link
              type="primary"
              @click="openJsonDialog('工具输入约束', row.toolInputSchema)"
            >
              查看
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>

    <el-dialog
      v-model="jsonVisible"
      :title="jsonTitle"
      width="760px"
    >
      <pre class="json-box">{{ formatJson(jsonContent) }}</pre>
    </el-dialog>
  </main>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref, watch } from "vue";
import { ElMessage } from "element-plus";
import {
  getAgentPlanDetail,
  pageAgentPlans
} from "../../api/agentPlan";
import type {
  AgentPlanInfo,
  AgentPlanQuery
} from "../../api/types";

const query = reactive<AgentPlanQuery>({
  pageNum: 1,
  pageSize: 10,
  traceId: "",
  userId: "",
  conversationId: "",
  intentCode: "",
  status: "",
  startTime: "",
  endTime: ""
});

const timeRange = ref<string[] | null>(null);
const plans = ref<AgentPlanInfo[]>([]);
const total = ref(0);
const loading = ref(false);
const currentPlan = ref<AgentPlanInfo | null>(null);
const detailVisible = ref(false);
const jsonVisible = ref(false);
const jsonTitle = ref("");
const jsonContent = ref("");

onMounted(() => {
  loadPlans();
});

watch(timeRange, value => {
  query.startTime = value?.[0] || "";
  query.endTime = value?.[1] || "";
});

async function loadPlans() {
  loading.value = true;

  try {
    const page = await pageAgentPlans(query);
    plans.value = page.records || [];
    total.value = page.total || 0;
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : "Agent 计划加载失败");
  } finally {
    loading.value = false;
  }
}

function handleSearch() {
  query.pageNum = 1;
  loadPlans();
}

function resetSearch() {
  query.pageNum = 1;
  query.pageSize = 10;
  query.traceId = "";
  query.userId = "";
  query.conversationId = "";
  query.intentCode = "";
  query.status = "";
  query.startTime = "";
  query.endTime = "";
  timeRange.value = null;
  loadPlans();
}

async function openDetail(row: AgentPlanInfo) {
  try {
    currentPlan.value = await getAgentPlanDetail(row.id);
    detailVisible.value = true;
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : "计划详情加载失败");
  }
}

function openJsonDialog(title: string, content?: string) {
  jsonTitle.value = title;
  jsonContent.value = content || "";
  jsonVisible.value = true;
}

function formatJson(value?: string) {
  if (!value) {
    return "-";
  }

  try {
    return JSON.stringify(JSON.parse(value), null, 2);
  } catch (error) {
    return value;
  }
}

function missingParamCount(plan: AgentPlanInfo) {
  try {
    const params = JSON.parse(plan.missingParamsJson || "[]");
    return Array.isArray(params) ? params.length : 0;
  } catch (error) {
    return "-";
  }
}

function statusText(status?: string) {
  const map: Record<string, string> = {
    PLANNED: "已计划",
    NEED_CLARIFICATION: "需补充",
    COMPLETED: "已完成",
    FAILED: "失败"
  };
  return status ? map[status] || status : "-";
}

function statusType(status?: string) {
  const map: Record<string, string> = {
    PLANNED: "primary",
    NEED_CLARIFICATION: "warning",
    COMPLETED: "success",
    FAILED: "danger"
  };
  return status ? map[status] || "info" : "info";
}

function stepStatusText(status?: string) {
  const map: Record<string, string> = {
    PENDING: "待执行",
    RUNNING: "执行中",
    COMPLETED: "已完成",
    SKIPPED: "跳过",
    FAILED: "失败"
  };
  return status ? map[status] || status : "-";
}

function stepStatusType(status?: string) {
  const map: Record<string, string> = {
    PENDING: "info",
    RUNNING: "warning",
    COMPLETED: "success",
    SKIPPED: "info",
    FAILED: "danger"
  };
  return status ? map[status] || "info" : "info";
}
</script>

<style scoped>
.admin-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.page-header {
  padding: 24px;
  border-radius: 18px;
  background: #ffffff;
  border: 1px solid #e5e7eb;
  display: flex;
  justify-content: space-between;
  gap: 16px;
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
  margin: 0;
  color: #2563eb;
  font-size: 13px;
  font-weight: 700;
}

.filter-card,
.table-card {
  padding: 18px;
  border-radius: 18px;
  background: #ffffff;
  border: 1px solid #e5e7eb;
}

.filter-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

.pagination-row {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

.ellipsis-text {
  display: inline-block;
  max-width: 210px;
  overflow: hidden;
  text-overflow: ellipsis;
  vertical-align: middle;
  white-space: nowrap;
}

.detail-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.detail-grid div {
  padding: 12px;
  border-radius: 12px;
  background: #f9fafb;
  border: 1px solid #e5e7eb;
}

.detail-grid span {
  display: block;
  color: #6b7280;
  font-size: 12px;
  margin-bottom: 4px;
}

.detail-grid strong {
  color: #111827;
  word-break: break-all;
}

.text-block {
  padding: 12px;
  border-radius: 12px;
  background: #f9fafb;
  border: 1px solid #e5e7eb;
  color: #374151;
  line-height: 1.7;
}

.json-actions {
  display: flex;
  gap: 10px;
  margin: 12px 0 18px;
}

.json-box {
  max-height: 360px;
  overflow: auto;
  padding: 14px;
  border-radius: 12px;
  background: #0f172a;
  color: #e5e7eb;
  font-size: 13px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
}
</style>
