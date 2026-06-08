<template>
  <main class="admin-page">
    <section class="page-header">
      <div>
        <p class="eyebrow">Agent Observability</p>
        <h1>Agent 调用链路日志</h1>
        <p>查看 AI 对话、工具调用、输入输出、耗时和异常信息。</p>
      </div>

      <el-button type="primary" :loading="loading" @click="loadLogs">
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
                <el-option label="成功" value="SUCCESS" />
                <el-option label="失败" value="FAILED" />
              </el-select>
            </el-form-item>
          </el-col>

          <el-col :span="6">
            <el-form-item label="意图">
              <el-input
                v-model.trim="query.intentCode"
                placeholder="AGENT_CHAT"
                clearable
              />
            </el-form-item>
          </el-col>

          <el-col :span="6">
            <el-form-item label="工具">
              <el-input
                v-model.trim="query.toolName"
                placeholder="ResumeAnalyzeTool"
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
        :data="logs"
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
        <el-table-column prop="intentCode" label="意图" width="140" />

        <el-table-column prop="toolName" label="工具" min-width="160">
          <template #default="{ row }">
            <span>{{ row.toolName || "-" }}</span>
          </template>
        </el-table-column>

        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 'SUCCESS' ? 'success' : 'danger'">
              {{ row.status === "SUCCESS" ? "成功" : "失败" }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column prop="costTime" label="耗时" width="100">
          <template #default="{ row }">
            <span>{{ row.costTime || 0 }} ms</span>
          </template>
        </el-table-column>

        <el-table-column prop="errorMsg" label="异常" min-width="180">
          <template #default="{ row }">
            <span class="error-text">{{ row.errorMsg || "-" }}</span>
          </template>
        </el-table-column>

        <el-table-column prop="createTime" label="创建时间" width="170" />

        <el-table-column label="操作" fixed="right" width="160">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">
              查看详情
            </el-button>
            <el-button link type="success" @click="openJsonDialog('输入数据', row.inputData)">
              输入
            </el-button>
            <el-button link type="warning" @click="openJsonDialog('输出数据', row.outputData)">
              输出
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
          @size-change="loadLogs"
          @current-change="loadLogs"
        />
      </div>
    </section>

    <el-dialog
      v-model="detailVisible"
      title="Agent Trace 详情"
      width="860px"
    >
      <div v-if="currentLog" class="detail-grid">
        <div>
          <span>TraceId</span>
          <strong>{{ currentLog.traceId }}</strong>
        </div>
        <div>
          <span>用户ID</span>
          <strong>{{ currentLog.userId }}</strong>
        </div>
        <div>
          <span>会话ID</span>
          <strong>{{ currentLog.conversationId || "-" }}</strong>
        </div>
        <div>
          <span>意图</span>
          <strong>{{ currentLog.intentCode || "-" }}</strong>
        </div>
        <div>
          <span>工具</span>
          <strong>{{ currentLog.toolName || "-" }}</strong>
        </div>
        <div>
          <span>状态</span>
          <strong>{{ currentLog.status }}</strong>
        </div>
        <div>
          <span>耗时</span>
          <strong>{{ currentLog.costTime || 0 }} ms</strong>
        </div>
        <div>
          <span>创建时间</span>
          <strong>{{ currentLog.createTime || "-" }}</strong>
        </div>
      </div>

      <el-divider />

      <h3>输入数据</h3>
      <pre class="json-box">{{ formatJson(currentLog?.inputData) }}</pre>

      <h3>输出数据</h3>
      <pre class="json-box">{{ formatJson(currentLog?.outputData) }}</pre>

      <h3 v-if="currentLog?.errorMsg">异常信息</h3>
      <pre v-if="currentLog?.errorMsg" class="json-box error-box">{{ currentLog.errorMsg }}</pre>
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
  getAgentTraceLogDetail,
  pageAgentTraceLogs
} from "../../api/agentTrace";
import type {
  AgentTraceLogInfo,
  AgentTraceLogQuery
} from "../../api/types";

/**
 * 查询参数。
 * 说明：后台查询表单和分页共用这一个对象。
 */
const query = reactive<AgentTraceLogQuery>({
  pageNum: 1,
  pageSize: 10,
  traceId: "",
  userId: "",
  conversationId: "",
  intentCode: "",
  toolName: "",
  status: "",
  startTime: "",
  endTime: ""
});

/**
 * 时间范围组件绑定值。
 */
const timeRange = ref<string[] | null>(null);

/**
 * 表格数据。
 */
const logs = ref<AgentTraceLogInfo[]>([]);

/**
 * 总条数。
 */
const total = ref(0);

/**
 * 加载状态。
 */
const loading = ref(false);

/**
 * 当前详情数据。
 */
const currentLog = ref<AgentTraceLogInfo | null>(null);

/**
 * 详情弹窗。
 */
const detailVisible = ref(false);

/**
 * JSON 弹窗。
 */
const jsonVisible = ref(false);
const jsonTitle = ref("");
const jsonContent = ref("");

onMounted(() => {
  loadLogs();
});

/**
 * 监听时间范围变化，同步到查询对象。
 */
watch(timeRange, value => {
  query.startTime = value?.[0] || "";
  query.endTime = value?.[1] || "";
});

/**
 * 加载 Agent Trace 日志。
 */
async function loadLogs() {
  loading.value = true;

  try {
    const page = await pageAgentTraceLogs(query);

    logs.value = page.records || [];
    total.value = page.total || 0;
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : "Agent Trace 日志加载失败");
  } finally {
    loading.value = false;
  }
}

/**
 * 点击查询。
 */
function handleSearch() {
  query.pageNum = 1;
  loadLogs();
}

/**
 * 重置查询条件。
 */
function resetSearch() {
  query.pageNum = 1;
  query.pageSize = 10;
  query.traceId = "";
  query.userId = "";
  query.conversationId = "";
  query.intentCode = "";
  query.toolName = "";
  query.status = "";
  query.startTime = "";
  query.endTime = "";
  timeRange.value = null;
  loadLogs();
}

/**
 * 打开详情弹窗。
 *
 * @param row 当前行
 */
async function openDetail(row: AgentTraceLogInfo) {
  try {
    currentLog.value = await getAgentTraceLogDetail(row.id);
    detailVisible.value = true;
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : "详情加载失败");
  }
}

/**
 * 打开 JSON 查看弹窗。
 *
 * @param title 弹窗标题
 * @param content JSON 内容
 */
function openJsonDialog(title: string, content?: string) {
  jsonTitle.value = title;
  jsonContent.value = content || "";
  jsonVisible.value = true;
}

/**
 * 格式化 JSON。
 *
 * @param value 原始 JSON 字符串
 */
function formatJson(value?: string) {
  if (!value) {
    return "-";
  }

  try {
    return JSON.stringify(JSON.parse(value), null, 2);
  } catch (error) {
    /*
     * 如果不是合法 JSON，就直接原样展示。
     */
    return value;
  }
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

.error-text {
  color: #dc2626;
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

.error-box {
  background: #7f1d1d;
}
</style>