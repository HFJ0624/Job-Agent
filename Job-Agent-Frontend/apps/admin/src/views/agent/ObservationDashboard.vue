<template>
  <main class="observation-page">
    <section class="page-header">
      <div>
        <p class="eyebrow">Agent Observability</p>
        <h1>Agent 观测看板</h1>
        <p>统一查看调用量、失败分类、慢调用、告警规则和 Trace 保留策略。</p>
      </div>

      <div class="header-actions">
        <el-date-picker
          v-model="dashboardTimeRange"
          type="datetimerange"
          start-placeholder="开始时间"
          end-placeholder="结束时间"
          value-format="YYYY-MM-DD HH:mm:ss"
          clearable
        />
        <el-button type="primary" :loading="dashboardLoading" @click="loadDashboard">
          刷新看板
        </el-button>
        <el-button type="warning" :loading="alertEvaluating" @click="evaluateRules">
          评估告警
        </el-button>
      </div>
    </section>

    <section class="metric-grid">
      <article class="metric-card">
        <span>总事件</span>
        <strong>{{ dashboard.totalEvents }}</strong>
      </article>
      <article class="metric-card">
        <span>成功率</span>
        <strong>{{ formatPercent(dashboard.successRate) }}</strong>
      </article>
      <article class="metric-card danger">
        <span>失败 / 拦截</span>
        <strong>{{ dashboard.failedEvents }} / {{ dashboard.blockedEvents }}</strong>
      </article>
      <article class="metric-card">
        <span>平均耗时</span>
        <strong>{{ dashboard.avgDurationMs || 0 }} ms</strong>
      </article>
      <article class="metric-card">
        <span>总 Token</span>
        <strong>{{ formatNumber(dashboard.totalTokens) }}</strong>
      </article>
      <article class="metric-card">
        <span>总费用</span>
        <strong>{{ formatMoney(dashboard.totalCost) }}</strong>
      </article>
    </section>

    <el-tabs v-model="activeTab" class="content-tabs">
      <el-tab-pane label="概览" name="overview">
        <div class="two-column">
          <section class="panel">
            <div class="panel-title">
              <h2>事件类型分布</h2>
            </div>
            <el-table :data="dashboard.eventTypeStats" border>
              <el-table-column prop="name" label="类型" min-width="140" />
              <el-table-column prop="count" label="次数" width="100" />
              <el-table-column label="占比" width="120">
                <template #default="{ row }">{{ formatPercent(row.ratio) }}</template>
              </el-table-column>
              <el-table-column prop="avgDurationMs" label="平均耗时(ms)" width="140" />
            </el-table>
          </section>

          <section class="panel">
            <div class="panel-title">
              <h2>失败分类 Top</h2>
            </div>
            <el-table :data="dashboard.failureStats" border>
              <el-table-column prop="name" label="失败分类" min-width="180" />
              <el-table-column prop="count" label="次数" width="100" />
              <el-table-column label="占比" width="120">
                <template #default="{ row }">{{ formatPercent(row.ratio) }}</template>
              </el-table-column>
              <el-table-column prop="lastTime" label="最近时间" min-width="170" />
            </el-table>
          </section>
        </div>

        <div class="two-column">
          <section class="panel">
            <div class="panel-title">
              <h2>慢模型 Top</h2>
            </div>
            <el-table :data="dashboard.slowModelStats" border>
              <el-table-column prop="name" label="模型/场景" min-width="180" />
              <el-table-column prop="count" label="次数" width="90" />
              <el-table-column prop="avgDurationMs" label="平均耗时(ms)" width="140" />
              <el-table-column prop="maxDurationMs" label="最大耗时(ms)" width="140" />
            </el-table>
          </section>

          <section class="panel">
            <div class="panel-title">
              <h2>慢工具 Top</h2>
            </div>
            <el-table :data="dashboard.slowToolStats" border>
              <el-table-column prop="name" label="工具" min-width="180" />
              <el-table-column prop="count" label="次数" width="90" />
              <el-table-column prop="avgDurationMs" label="平均耗时(ms)" width="140" />
              <el-table-column prop="maxDurationMs" label="最大耗时(ms)" width="140" />
            </el-table>
          </section>
        </div>

        <section class="panel">
          <div class="panel-title">
            <h2>最近告警</h2>
          </div>
          <el-table :data="dashboard.recentAlerts" border>
            <el-table-column prop="id" label="ID" width="80" />
            <el-table-column prop="ruleName" label="规则" min-width="180" />
            <el-table-column prop="ruleType" label="类型" min-width="160" />
            <el-table-column prop="alertLevel" label="级别" width="100" />
            <el-table-column prop="metricValue" label="当前值" width="110" />
            <el-table-column prop="thresholdValue" label="阈值" width="110" />
            <el-table-column prop="status" label="状态" width="110" />
            <el-table-column prop="createTime" label="时间" width="170" />
            <el-table-column prop="alertMessage" label="说明" min-width="240" show-overflow-tooltip />
          </el-table>
        </section>
      </el-tab-pane>

      <el-tab-pane label="事件明细" name="events">
        <section class="panel">
          <el-form :model="eventQuery" inline>
            <el-form-item label="TraceId">
              <el-input v-model.trim="eventQuery.traceId" clearable placeholder="traceId" />
            </el-form-item>
            <el-form-item label="类型">
              <el-select v-model="eventQuery.eventType" clearable placeholder="全部" style="width: 150px">
                <el-option v-for="item in eventTypes" :key="item" :label="item" :value="item" />
              </el-select>
            </el-form-item>
            <el-form-item label="状态">
              <el-select v-model="eventQuery.status" clearable placeholder="全部" style="width: 150px">
                <el-option v-for="item in eventStatuses" :key="item" :label="item" :value="item" />
              </el-select>
            </el-form-item>
            <el-form-item label="失败分类">
              <el-select v-model="eventQuery.errorCategory" clearable placeholder="全部" style="width: 190px">
                <el-option v-for="item in errorCategories" :key="item" :label="item" :value="item" />
              </el-select>
            </el-form-item>
            <el-form-item label="模型">
              <el-input v-model.trim="eventQuery.modelCode" clearable placeholder="modelCode" />
            </el-form-item>
            <el-form-item label="工具">
              <el-input v-model.trim="eventQuery.toolName" clearable placeholder="toolName" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="eventLoading" @click="searchEvents">查询</el-button>
              <el-button @click="resetEvents">重置</el-button>
            </el-form-item>
          </el-form>

          <el-table v-loading="eventLoading" :data="events" border>
            <el-table-column prop="id" label="ID" width="80" />
            <el-table-column prop="traceId" label="TraceId" min-width="210" show-overflow-tooltip />
            <el-table-column prop="eventType" label="类型" width="110" />
            <el-table-column prop="eventName" label="事件" min-width="180" show-overflow-tooltip />
            <el-table-column prop="status" label="状态" width="110">
              <template #default="{ row }">
                <el-tag :type="statusTag(row.status)">{{ row.status }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="errorCategory" label="失败分类" min-width="160" />
            <el-table-column prop="modelCode" label="模型" min-width="150" show-overflow-tooltip />
            <el-table-column prop="toolName" label="工具" min-width="180" show-overflow-tooltip />
            <el-table-column prop="durationMs" label="耗时(ms)" width="110" />
            <el-table-column prop="totalTokens" label="Token" width="100" />
            <el-table-column prop="totalCost" label="费用" width="100" />
            <el-table-column prop="createTime" label="时间" width="170" />
            <el-table-column label="操作" fixed="right" width="120">
              <template #default="{ row }">
                <el-button link type="primary" @click="openEventDetail(row.id)">详情</el-button>
              </template>
            </el-table-column>
          </el-table>

          <div class="pager">
            <el-pagination
              v-model:current-page="eventQuery.pageNum"
              v-model:page-size="eventQuery.pageSize"
              :total="eventTotal"
              :page-sizes="[10, 20, 50, 100]"
              layout="total, sizes, prev, pager, next, jumper"
              @size-change="loadEvents"
              @current-change="loadEvents"
            />
          </div>
        </section>
      </el-tab-pane>

      <el-tab-pane label="告警规则" name="rules">
        <section class="panel">
          <div class="panel-title">
            <h2>告警规则</h2>
            <el-button type="primary" @click="openRuleDialog()">新增规则</el-button>
          </div>
          <el-table v-loading="ruleLoading" :data="alertRules" border>
            <el-table-column prop="id" label="ID" width="80" />
            <el-table-column prop="ruleName" label="规则名称" min-width="180" />
            <el-table-column prop="ruleType" label="规则类型" min-width="170" />
            <el-table-column prop="eventType" label="事件类型" width="120" />
            <el-table-column prop="errorCategory" label="失败分类" min-width="160" />
            <el-table-column prop="thresholdValue" label="阈值" width="100" />
            <el-table-column prop="windowMinutes" label="窗口(分)" width="100" />
            <el-table-column prop="cooldownMinutes" label="冷却(分)" width="100" />
            <el-table-column prop="status" label="状态" width="110">
              <template #default="{ row }">
                <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'">{{ row.status }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="lastAlertTime" label="最近告警" width="170" />
            <el-table-column label="操作" fixed="right" width="150">
              <template #default="{ row }">
                <el-button link type="primary" @click="openRuleDialog(row)">编辑</el-button>
                <el-popconfirm title="确认删除这条告警规则？" @confirm="removeRule(row)">
                  <template #reference>
                    <el-button link type="danger">删除</el-button>
                  </template>
                </el-popconfirm>
              </template>
            </el-table-column>
          </el-table>
          <div class="pager">
            <el-pagination
              v-model:current-page="ruleQuery.pageNum"
              v-model:page-size="ruleQuery.pageSize"
              :total="ruleTotal"
              layout="total, sizes, prev, pager, next"
              @size-change="loadRules"
              @current-change="loadRules"
            />
          </div>
        </section>
      </el-tab-pane>

      <el-tab-pane label="告警记录" name="alerts">
        <section class="panel">
          <el-form :model="alertRecordQuery" inline>
            <el-form-item label="状态">
              <el-select v-model="alertRecordQuery.status" clearable placeholder="全部" style="width: 150px">
                <el-option label="OPEN" value="OPEN" />
                <el-option label="RESOLVED" value="RESOLVED" />
                <el-option label="IGNORED" value="IGNORED" />
              </el-select>
            </el-form-item>
            <el-form-item label="类型">
              <el-select v-model="alertRecordQuery.ruleType" clearable placeholder="全部" style="width: 190px">
                <el-option v-for="item in ruleTypes" :key="item" :label="item" :value="item" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="alertRecordLoading" @click="loadAlertRecords">查询</el-button>
              <el-button @click="resetAlertRecords">重置</el-button>
            </el-form-item>
          </el-form>

          <el-table v-loading="alertRecordLoading" :data="alertRecords" border>
            <el-table-column prop="id" label="ID" width="80" />
            <el-table-column prop="ruleName" label="规则" min-width="180" />
            <el-table-column prop="ruleType" label="类型" min-width="170" />
            <el-table-column prop="alertLevel" label="级别" width="100" />
            <el-table-column prop="metricValue" label="当前值" width="110" />
            <el-table-column prop="thresholdValue" label="阈值" width="110" />
            <el-table-column prop="status" label="状态" width="110" />
            <el-table-column prop="alertMessage" label="说明" min-width="260" show-overflow-tooltip />
            <el-table-column prop="createTime" label="时间" width="170" />
            <el-table-column label="操作" fixed="right" width="150">
              <template #default="{ row }">
                <el-button link type="success" @click="changeAlertStatus(row.id, 'RESOLVED')">处理</el-button>
                <el-button link type="info" @click="changeAlertStatus(row.id, 'IGNORED')">忽略</el-button>
              </template>
            </el-table-column>
          </el-table>

          <div class="pager">
            <el-pagination
              v-model:current-page="alertRecordQuery.pageNum"
              v-model:page-size="alertRecordQuery.pageSize"
              :total="alertRecordTotal"
              layout="total, sizes, prev, pager, next"
              @size-change="loadAlertRecords"
              @current-change="loadAlertRecords"
            />
          </div>
        </section>
      </el-tab-pane>

      <el-tab-pane label="Trace 保留策略" name="retention">
        <section class="panel">
          <div class="panel-title">
            <h2>Trace 保留策略</h2>
            <el-button type="primary" @click="openRetentionDialog()">新增策略</el-button>
          </div>
          <el-table v-loading="retentionLoading" :data="retentionPolicies" border>
            <el-table-column prop="id" label="ID" width="80" />
            <el-table-column prop="policyName" label="策略名称" min-width="180" />
            <el-table-column prop="targetTable" label="目标表" min-width="200" />
            <el-table-column prop="retentionDays" label="保留天数" width="110" />
            <el-table-column prop="batchSize" label="批次大小" width="110" />
            <el-table-column prop="status" label="状态" width="110" />
            <el-table-column prop="lastDeletedCount" label="上次清理" width="110" />
            <el-table-column prop="lastExecuteTime" label="上次执行" width="170" />
            <el-table-column label="操作" fixed="right" width="220">
              <template #default="{ row }">
                <el-button link type="primary" @click="openRetentionDialog(row)">编辑</el-button>
                <el-button link type="warning" @click="previewRetention(row)">预览</el-button>
                <el-button link type="danger" @click="executeRetention(row)">执行</el-button>
              </template>
            </el-table-column>
          </el-table>
        </section>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="eventDetailVisible" title="观测事件详情" width="900px">
      <div v-if="currentEvent" class="detail-grid">
        <div><span>TraceId</span><strong>{{ currentEvent.traceId || "-" }}</strong></div>
        <div><span>类型</span><strong>{{ currentEvent.eventType || "-" }}</strong></div>
        <div><span>事件</span><strong>{{ currentEvent.eventName || "-" }}</strong></div>
        <div><span>状态</span><strong>{{ currentEvent.status || "-" }}</strong></div>
        <div><span>失败分类</span><strong>{{ currentEvent.errorCategory || "-" }}</strong></div>
        <div><span>耗时</span><strong>{{ currentEvent.durationMs || 0 }} ms</strong></div>
      </div>
      <el-divider />
      <h3>请求快照</h3>
      <pre class="json-box">{{ formatJson(currentEvent?.requestSnapshot) }}</pre>
      <h3>响应快照</h3>
      <pre class="json-box">{{ formatJson(currentEvent?.responseSnapshot) }}</pre>
      <h3 v-if="currentEvent?.errorMsg">错误信息</h3>
      <pre v-if="currentEvent?.errorMsg" class="json-box error-box">{{ currentEvent.errorMsg }}</pre>
    </el-dialog>

    <el-dialog v-model="ruleDialogVisible" :title="ruleForm.id ? '编辑告警规则' : '新增告警规则'" width="720px">
      <el-form :model="ruleForm" label-width="120px">
        <el-form-item label="规则名称">
          <el-input v-model.trim="ruleForm.ruleName" />
        </el-form-item>
        <el-form-item label="规则类型">
          <el-select v-model="ruleForm.ruleType" style="width: 100%">
            <el-option v-for="item in ruleTypes" :key="item" :label="item" :value="item" />
          </el-select>
        </el-form-item>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="事件类型">
              <el-select v-model="ruleForm.eventType" clearable style="width: 100%">
                <el-option v-for="item in eventTypes" :key="item" :label="item" :value="item" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="失败分类">
              <el-select v-model="ruleForm.errorCategory" clearable style="width: 100%">
                <el-option v-for="item in errorCategories" :key="item" :label="item" :value="item" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="模型">
              <el-input v-model.trim="ruleForm.modelCode" placeholder="可为空" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="工具">
              <el-input v-model.trim="ruleForm.toolName" placeholder="可为空" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="8">
            <el-form-item label="阈值">
              <el-input-number v-model="ruleForm.thresholdValue" :min="0" :precision="2" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="窗口分钟">
              <el-input-number v-model="ruleForm.windowMinutes" :min="1" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="最小样本">
              <el-input-number v-model="ruleForm.minSampleCount" :min="1" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="8">
            <el-form-item label="冷却分钟">
              <el-input-number v-model="ruleForm.cooldownMinutes" :min="1" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="级别">
              <el-select v-model="ruleForm.alertLevel" style="width: 100%">
                <el-option label="INFO" value="INFO" />
                <el-option label="WARN" value="WARN" />
                <el-option label="CRITICAL" value="CRITICAL" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="状态">
              <el-select v-model="ruleForm.status" style="width: 100%">
                <el-option label="ACTIVE" value="ACTIVE" />
                <el-option label="DISABLED" value="DISABLED" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="备注">
          <el-input v-model="ruleForm.remark" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="ruleDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveRule">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="retentionDialogVisible" :title="retentionForm.id ? '编辑保留策略' : '新增保留策略'" width="620px">
      <el-form :model="retentionForm" label-width="120px">
        <el-form-item label="策略名称">
          <el-input v-model.trim="retentionForm.policyName" />
        </el-form-item>
        <el-form-item label="目标表">
          <el-select v-model="retentionForm.targetTable" style="width: 100%">
            <el-option label="统一观测事件" value="agent_observation_event" />
            <el-option label="Agent Trace 日志" value="agent_trace_log" />
            <el-option label="模型调用日志" value="ai_model_call_log" />
          </el-select>
        </el-form-item>
        <el-row :gutter="12">
          <el-col :span="8">
            <el-form-item label="保留天数">
              <el-input-number v-model="retentionForm.retentionDays" :min="1" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="批次大小">
              <el-input-number v-model="retentionForm.batchSize" :min="1" :max="5000" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="状态">
              <el-select v-model="retentionForm.status" style="width: 100%">
                <el-option label="ACTIVE" value="ACTIVE" />
                <el-option label="DISABLED" value="DISABLED" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="备注">
          <el-input v-model="retentionForm.remark" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="retentionDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveRetention">保存</el-button>
      </template>
    </el-dialog>
  </main>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref, watch } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import {
  createObservationAlertRule,
  createTraceRetentionPolicy,
  deleteObservationAlertRule,
  evaluateObservationAlertRules,
  executeTraceRetentionPolicy,
  getObservationDashboard,
  getObservationEventDetail,
  listTraceRetentionPolicies,
  pageObservationAlertRecords,
  pageObservationAlertRules,
  pageObservationEvents,
  previewTraceRetentionPolicy,
  updateObservationAlertRecordStatus,
  updateObservationAlertRule,
  updateTraceRetentionPolicy
} from "../../api/agentObservation";
import type {
  AgentObservationAlertRecordInfo,
  AgentObservationAlertRecordQuery,
  AgentObservationAlertRuleInfo,
  AgentObservationAlertRuleQuery,
  AgentObservationDashboard,
  AgentObservationDashboardQuery,
  AgentObservationEventInfo,
  AgentObservationEventQuery,
  AgentTraceRetentionPolicyInfo
} from "../../api/types";

const eventTypes = ["MODEL", "TOOL", "EXECUTOR", "GUARDRAIL", "RAG", "TRACE"];
const eventStatuses = ["SUCCESS", "FAILED", "BLOCKED", "SKIPPED"];
const errorCategories = [
  "CONFIG_ERROR",
  "MODEL_ERROR",
  "TOOL_ERROR",
  "TOOL_CONFIRMATION",
  "GUARDRAIL_BLOCKED",
  "RAG_ERROR",
  "TIMEOUT",
  "PERMISSION_DENIED",
  "PARAM_MISSING",
  "SYSTEM_ERROR"
];
const ruleTypes = ["FAILURE_RATE", "ERROR_CATEGORY_COUNT", "AVG_DURATION", "TOTAL_COST", "GUARDRAIL_BLOCK_COUNT"];

const activeTab = ref("overview");
const dashboardLoading = ref(false);
const eventLoading = ref(false);
const ruleLoading = ref(false);
const alertRecordLoading = ref(false);
const retentionLoading = ref(false);
const alertEvaluating = ref(false);
const saving = ref(false);
const eventDetailVisible = ref(false);
const ruleDialogVisible = ref(false);
const retentionDialogVisible = ref(false);

const dashboardTimeRange = ref<string[] | null>(null);
const dashboardQuery = reactive<AgentObservationDashboardQuery>({
  startTime: "",
  endTime: ""
});

const dashboard = reactive<AgentObservationDashboard>({
  totalEvents: 0,
  successEvents: 0,
  failedEvents: 0,
  blockedEvents: 0,
  skippedEvents: 0,
  successRate: 0,
  avgDurationMs: 0,
  totalTokens: 0,
  totalCost: 0,
  eventTypeStats: [],
  failureStats: [],
  slowModelStats: [],
  slowToolStats: [],
  recentAlerts: []
});

const eventQuery = reactive<AgentObservationEventQuery>({
  pageNum: 1,
  pageSize: 10,
  traceId: "",
  eventType: "",
  status: "",
  errorCategory: "",
  modelCode: "",
  toolName: ""
});
const events = ref<AgentObservationEventInfo[]>([]);
const eventTotal = ref(0);
const currentEvent = ref<AgentObservationEventInfo | null>(null);

const ruleQuery = reactive<AgentObservationAlertRuleQuery>({
  pageNum: 1,
  pageSize: 10,
  ruleName: "",
  ruleType: "",
  status: ""
});
const alertRules = ref<AgentObservationAlertRuleInfo[]>([]);
const ruleTotal = ref(0);
const ruleForm = reactive<AgentObservationAlertRuleInfo>(defaultRuleForm());

const alertRecordQuery = reactive<AgentObservationAlertRecordQuery>({
  pageNum: 1,
  pageSize: 10,
  ruleType: "",
  status: ""
});
const alertRecords = ref<AgentObservationAlertRecordInfo[]>([]);
const alertRecordTotal = ref(0);

const retentionPolicies = ref<AgentTraceRetentionPolicyInfo[]>([]);
const retentionForm = reactive<AgentTraceRetentionPolicyInfo>(defaultRetentionForm());

onMounted(() => {
  loadAll();
});

watch(dashboardTimeRange, value => {
  dashboardQuery.startTime = value?.[0] || "";
  dashboardQuery.endTime = value?.[1] || "";
});

/**
 * 首屏加载全部观测数据。
 *
 * 方法步骤:
 * 1. 看板指标和事件明细用于排障入口。
 * 2. 告警规则、告警记录和保留策略用于管理操作。
 * 3. 这些请求互不依赖，可以并行加载。
 */
async function loadAll() {
  await Promise.all([
    loadDashboard(),
    loadEvents(),
    loadRules(),
    loadAlertRecords(),
    loadRetentionPolicies()
  ]);
}

async function loadDashboard() {
  dashboardLoading.value = true;
  try {
    const result = await getObservationDashboard(dashboardQuery);
    Object.assign(dashboard, result);
  } finally {
    dashboardLoading.value = false;
  }
}

async function loadEvents() {
  eventLoading.value = true;
  try {
    const page = await pageObservationEvents(eventQuery);
    events.value = page.records || [];
    eventTotal.value = page.total || 0;
  } finally {
    eventLoading.value = false;
  }
}

function searchEvents() {
  eventQuery.pageNum = 1;
  loadEvents();
}

function resetEvents() {
  eventQuery.pageNum = 1;
  eventQuery.pageSize = 10;
  eventQuery.traceId = "";
  eventQuery.eventType = "";
  eventQuery.status = "";
  eventQuery.errorCategory = "";
  eventQuery.modelCode = "";
  eventQuery.toolName = "";
  loadEvents();
}

async function openEventDetail(id: number) {
  currentEvent.value = await getObservationEventDetail(id);
  eventDetailVisible.value = true;
}

async function loadRules() {
  ruleLoading.value = true;
  try {
    const page = await pageObservationAlertRules(ruleQuery);
    alertRules.value = page.records || [];
    ruleTotal.value = page.total || 0;
  } finally {
    ruleLoading.value = false;
  }
}

function openRuleDialog(row?: AgentObservationAlertRuleInfo) {
  Object.assign(ruleForm, row ? { ...row } : defaultRuleForm());
  ruleDialogVisible.value = true;
}

async function saveRule() {
  if (!ruleForm.ruleName || !ruleForm.ruleType) {
    ElMessage.warning("请填写规则名称和规则类型");
    return;
  }
  saving.value = true;
  try {
    if (ruleForm.id) {
      await updateObservationAlertRule(ruleForm.id, ruleForm);
    } else {
      await createObservationAlertRule(ruleForm);
    }
    ElMessage.success("告警规则已保存");
    ruleDialogVisible.value = false;
    await loadRules();
  } finally {
    saving.value = false;
  }
}

async function removeRule(row: AgentObservationAlertRuleInfo) {
  if (!row.id) {
    return;
  }
  await deleteObservationAlertRule(row.id);
  ElMessage.success("告警规则已删除");
  await loadRules();
}

async function evaluateRules() {
  alertEvaluating.value = true;
  try {
    const records = await evaluateObservationAlertRules();
    ElMessage.success(`评估完成，新增 ${records.length} 条告警`);
    await Promise.all([loadDashboard(), loadRules(), loadAlertRecords()]);
  } finally {
    alertEvaluating.value = false;
  }
}

async function loadAlertRecords() {
  alertRecordLoading.value = true;
  try {
    const page = await pageObservationAlertRecords(alertRecordQuery);
    alertRecords.value = page.records || [];
    alertRecordTotal.value = page.total || 0;
  } finally {
    alertRecordLoading.value = false;
  }
}

function resetAlertRecords() {
  alertRecordQuery.pageNum = 1;
  alertRecordQuery.pageSize = 10;
  alertRecordQuery.ruleType = "";
  alertRecordQuery.status = "";
  loadAlertRecords();
}

async function changeAlertStatus(id: number, status: string) {
  await updateObservationAlertRecordStatus(id, status);
  ElMessage.success("告警状态已更新");
  await Promise.all([loadDashboard(), loadAlertRecords()]);
}

async function loadRetentionPolicies() {
  retentionLoading.value = true;
  try {
    retentionPolicies.value = await listTraceRetentionPolicies();
  } finally {
    retentionLoading.value = false;
  }
}

function openRetentionDialog(row?: AgentTraceRetentionPolicyInfo) {
  Object.assign(retentionForm, row ? { ...row } : defaultRetentionForm());
  retentionDialogVisible.value = true;
}

async function saveRetention() {
  if (!retentionForm.policyName || !retentionForm.targetTable) {
    ElMessage.warning("请填写策略名称和目标表");
    return;
  }
  saving.value = true;
  try {
    if (retentionForm.id) {
      await updateTraceRetentionPolicy(retentionForm.id, retentionForm);
    } else {
      await createTraceRetentionPolicy(retentionForm);
    }
    ElMessage.success("保留策略已保存");
    retentionDialogVisible.value = false;
    await loadRetentionPolicies();
  } finally {
    saving.value = false;
  }
}

async function previewRetention(row: AgentTraceRetentionPolicyInfo) {
  if (!row.id) {
    return;
  }
  const preview = await previewTraceRetentionPolicy(row.id);
  ElMessage.info(`命中 ${preview.matchedCount} 条，截止时间 ${preview.cutoffTime || "-"}`);
}

async function executeRetention(row: AgentTraceRetentionPolicyInfo) {
  if (!row.id) {
    return;
  }
  const preview = await previewTraceRetentionPolicy(row.id);
  await ElMessageBox.confirm(
    `当前策略将逻辑删除 ${preview.matchedCount} 条数据，本次最多处理 ${preview.batchSize} 条，确认执行？`,
    "执行 Trace 保留策略",
    { type: "warning" }
  );
  await executeTraceRetentionPolicy(row.id);
  ElMessage.success("保留策略已执行");
  await loadRetentionPolicies();
}

function defaultRuleForm(): AgentObservationAlertRuleInfo {
  return {
    ruleName: "",
    ruleType: "FAILURE_RATE",
    eventType: "",
    errorCategory: "",
    modelCode: "",
    toolName: "",
    thresholdValue: 20,
    windowMinutes: 10,
    minSampleCount: 1,
    cooldownMinutes: 30,
    alertLevel: "WARN",
    status: "ACTIVE",
    remark: ""
  };
}

function defaultRetentionForm(): AgentTraceRetentionPolicyInfo {
  return {
    policyName: "",
    targetTable: "agent_observation_event",
    retentionDays: 30,
    batchSize: 1000,
    status: "ACTIVE",
    remark: ""
  };
}

function formatPercent(value?: number) {
  return `${Number(value || 0).toFixed(2)}%`;
}

function formatMoney(value?: number) {
  return Number(value || 0).toFixed(6);
}

function formatNumber(value?: number) {
  return Number(value || 0).toLocaleString();
}

function statusTag(status?: string) {
  if (status === "SUCCESS") {
    return "success";
  }
  if (status === "FAILED" || status === "BLOCKED") {
    return "danger";
  }
  return "info";
}

function formatJson(value?: string) {
  if (!value) {
    return "-";
  }
  try {
    return JSON.stringify(JSON.parse(value), null, 2);
  } catch {
    return value;
  }
}
</script>

<style scoped>
.observation-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  padding: 20px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #ffffff;
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

.header-actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 10px;
}

.metric-grid {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: 12px;
}

.metric-card {
  padding: 16px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #ffffff;
}

.metric-card span {
  color: #6b7280;
  font-size: 13px;
}

.metric-card strong {
  display: block;
  margin-top: 8px;
  color: #111827;
  font-size: 22px;
}

.metric-card.danger strong {
  color: #dc2626;
}

.content-tabs {
  border-radius: 8px;
  background: #ffffff;
}

.two-column {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
  margin-bottom: 16px;
}

.panel {
  padding: 16px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #ffffff;
}

.panel-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.panel-title h2 {
  margin: 0;
  font-size: 16px;
  color: #111827;
}

.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

.detail-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.detail-grid div {
  padding: 12px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #f9fafb;
}

.detail-grid span {
  display: block;
  color: #6b7280;
  font-size: 12px;
}

.detail-grid strong {
  color: #111827;
  word-break: break-all;
}

.json-box {
  max-height: 320px;
  overflow: auto;
  padding: 12px;
  border-radius: 8px;
  background: #0f172a;
  color: #e5e7eb;
  white-space: pre-wrap;
  word-break: break-word;
}

.error-box {
  background: #7f1d1d;
}

@media (max-width: 1200px) {
  .metric-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .two-column {
    grid-template-columns: 1fr;
  }
}
</style>
