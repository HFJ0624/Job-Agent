<template>
  <main class="admin-page">
    <section class="page-header">
      <div>
        <p class="eyebrow">Agent Eval</p>
        <h1>Eval 评测平台</h1>
        <p>管理评测数据集和用例，批量回归工具选择、参数、RAG 命中和回答质量。</p>
      </div>
      <div class="header-actions">
        <el-button :loading="running" @click="runAll">全量回归</el-button>
        <el-button type="primary" @click="openCaseDialog()">新增用例</el-button>
        <el-button type="success" @click="openDatasetDialog()">新增数据集</el-button>
      </div>
    </section>

    <section class="metric-grid">
      <div class="metric-card">
        <span>通过率</span>
        <strong>{{ latestRun ? formatPercent(latestPassRate) : "-" }}</strong>
        <small>{{ formatDelta(latestRun?.passRateDelta) }}</small>
      </div>
      <div class="metric-card">
        <span>工具准确率</span>
        <strong>{{ formatMetric(latestRun?.toolAccuracy) }}</strong>
        <small>{{ formatDelta(latestRun?.toolAccuracyDelta) }}</small>
      </div>
      <div class="metric-card">
        <span>参数准确率</span>
        <strong>{{ formatMetric(latestRun?.paramAccuracy) }}</strong>
        <small>{{ formatDelta(latestRun?.paramAccuracyDelta) }}</small>
      </div>
      <div class="metric-card">
        <span>RAG 命中率</span>
        <strong>{{ formatMetric(latestRun?.ragHitRate) }}</strong>
        <small>{{ formatDelta(latestRun?.ragHitRateDelta) }}</small>
      </div>
      <div class="metric-card">
        <span>回答质量均分</span>
        <strong>{{ latestRun?.answerQualityAvg ?? "-" }}</strong>
        <small>{{ formatDelta(latestRun?.answerQualityDelta) }}</small>
      </div>
    </section>

    <section class="table-card" v-if="latestFailureStats.length">
      <div class="section-title-row">
        <div>
          <h2>失败分类统计</h2>
          <p>来自最新运行批次，用来快速定位退化原因。</p>
        </div>
      </div>
      <div class="failure-stat-row">
        <el-tag v-for="item in latestFailureStats" :key="item.name" type="danger" effect="light">
          {{ item.name }}：{{ item.count }}
        </el-tag>
      </div>
    </section>

    <section class="table-card">
      <div class="section-title-row">
        <div>
          <h2>数据集</h2>
          <p>用数据集把同类用例分组，便于单独回归。</p>
        </div>
        <el-button :loading="loadingDatasets" @click="loadDatasets">刷新</el-button>
      </div>
      <el-table v-loading="loadingDatasets" :data="datasets" border stripe>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="datasetName" label="名称" min-width="160" />
        <el-table-column prop="datasetCode" label="编码" min-width="160" />
        <el-table-column prop="evalType" label="类型" width="150" />
        <el-table-column prop="enableStatus" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.enableStatus === 1 ? 'success' : 'info'">
              {{ row.enableStatus === 1 ? "启用" : "禁用" }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="170" />
        <el-table-column label="操作" fixed="right" width="260">
          <template #default="{ row }">
            <el-button link type="primary" @click="filterCasesByDataset(row)">查看用例</el-button>
            <el-button link type="success" :loading="running" @click="runDataset(row)">运行</el-button>
            <el-button link @click="openDatasetDialog(row)">编辑</el-button>
            <el-button link type="danger" @click="removeDataset(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <section class="table-card">
      <div class="section-title-row">
        <div>
          <h2>评测用例</h2>
          <p>配置用户输入、期望工具、参数、RAG 命中目标和回答关键词。</p>
        </div>
        <el-button :loading="loadingCases" @click="loadCases">刷新</el-button>
      </div>

      <el-form :model="caseQuery" label-width="80px" class="filter-form compact-filter">
        <el-row :gutter="12">
          <el-col :span="5">
            <el-form-item label="数据集">
              <el-select v-model="caseQuery.datasetId" clearable placeholder="全部">
                <el-option v-for="item in datasetOptions" :key="item.id" :label="item.datasetName" :value="item.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="5">
            <el-form-item label="名称">
              <el-input v-model.trim="caseQuery.caseName" clearable placeholder="用例名称" />
            </el-form-item>
          </el-col>
          <el-col :span="5">
            <el-form-item label="类型">
              <el-select v-model="caseQuery.evalType" clearable placeholder="全部">
                <el-option v-for="item in evalTypes" :key="item" :label="item" :value="item" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="5">
            <el-form-item label="工具">
              <el-input v-model.trim="caseQuery.expectedToolName" clearable placeholder="期望工具" />
            </el-form-item>
          </el-col>
          <el-col :span="4">
            <el-button type="primary" @click="searchCases">查询</el-button>
            <el-button @click="resetCaseQuery">重置</el-button>
          </el-col>
        </el-row>
      </el-form>

      <el-table v-loading="loadingCases" :data="cases" border stripe>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="caseName" label="用例" min-width="180" />
        <el-table-column prop="userId" label="用户ID" width="100" />
        <el-table-column prop="evalType" label="类型" width="140" />
        <el-table-column prop="expectedToolName" label="期望工具" min-width="160" />
        <el-table-column prop="expectedAnswerKeywords" label="答案关键词" min-width="180" />
        <el-table-column prop="enableStatus" label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.enableStatus === 1 ? 'success' : 'info'">
              {{ row.enableStatus === 1 ? "启用" : "禁用" }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" fixed="right" width="250">
          <template #default="{ row }">
            <el-button link type="success" :loading="running" @click="runCase(row)">运行</el-button>
            <el-button link type="primary" @click="openCaseDialog(row)">编辑</el-button>
            <el-button link @click="openJsonDialog('用户输入', row.inputMessage)">输入</el-button>
            <el-button link type="danger" @click="removeCase(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination-row">
        <el-pagination
          v-model:current-page="caseQuery.pageNum"
          v-model:page-size="caseQuery.pageSize"
          :total="caseTotal"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          @size-change="loadCases"
          @current-change="loadCases"
        />
      </div>
    </section>

    <section class="table-card">
      <div class="section-title-row">
        <div>
          <h2>运行批次</h2>
          <p>每次批量回归都会沉淀一条批次记录和指标。</p>
        </div>
        <el-button :loading="loadingRuns" @click="loadRuns">刷新</el-button>
      </div>
      <el-table v-loading="loadingRuns" :data="runs" border stripe>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="runName" label="名称" min-width="220" />
        <el-table-column prop="runType" label="类型" width="110" />
        <el-table-column label="通过" width="120">
          <template #default="{ row }">{{ row.passCount }}/{{ row.totalCount }}</template>
        </el-table-column>
        <el-table-column prop="toolAccuracy" label="工具%" width="100" />
        <el-table-column prop="paramAccuracy" label="参数%" width="100" />
        <el-table-column prop="ragHitRate" label="RAG%" width="100" />
        <el-table-column prop="answerQualityAvg" label="回答分" width="100" />
        <el-table-column prop="baselineFlag" label="基准" width="90">
          <template #default="{ row }">
            <el-tag v-if="row.baselineFlag === 1" type="warning">Baseline</el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="对比" width="130">
          <template #default="{ row }">
            <span>{{ row.compareRunId ? `#${row.compareRunId}` : "-" }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 'SUCCESS' ? 'success' : 'danger'">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="170" />
        <el-table-column label="操作" fixed="right" width="210">
          <template #default="{ row }">
            <el-button link type="primary" @click="filterResultsByRun(row)">查看结果</el-button>
            <el-button link type="warning" @click="markBaseline(row)">设为基准</el-button>
            <el-button link @click="openJsonDialog('失败统计', row.failureStatsJson)">统计</el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <section class="table-card">
      <div class="section-title-row">
        <div>
          <h2>评测结果</h2>
          <p>查看每条用例的断言结果、失败分类和实际输出。</p>
        </div>
        <el-button :loading="loadingResults" @click="loadResults">刷新</el-button>
      </div>
      <el-table v-loading="loadingResults" :data="results" border stripe>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="runId" label="批次" width="90" />
        <el-table-column prop="caseId" label="用例" width="90" />
        <el-table-column prop="evalType" label="类型" width="140" />
        <el-table-column prop="passStatus" label="通过" width="90">
          <template #default="{ row }">
            <el-tag :type="row.passStatus === 1 ? 'success' : 'danger'">
              {{ row.passStatus === 1 ? "通过" : "失败" }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="toolSelectPass" label="工具" width="90">
          <template #default="{ row }">{{ formatPass(row.toolSelectPass) }}</template>
        </el-table-column>
        <el-table-column prop="toolParamPass" label="参数" width="90">
          <template #default="{ row }">{{ formatPass(row.toolParamPass) }}</template>
        </el-table-column>
        <el-table-column prop="ragHitPass" label="RAG" width="90">
          <template #default="{ row }">{{ formatPass(row.ragHitPass) }}</template>
        </el-table-column>
        <el-table-column prop="answerQualityScore" label="回答分" width="100" />
        <el-table-column prop="judgeScore" label="Judge" width="100" />
        <el-table-column prop="answerScoreDelta" label="分数Δ" width="100">
          <template #default="{ row }">{{ formatDelta(row.answerScoreDelta, false) }}</template>
        </el-table-column>
        <el-table-column prop="failureType" label="失败分类" width="160" />
        <el-table-column prop="judgeReason" label="Judge原因" min-width="220" />
        <el-table-column prop="failReason" label="失败原因" min-width="220" />
        <el-table-column label="操作" fixed="right" width="230">
          <template #default="{ row }">
            <el-button link type="primary" @click="openJsonDialog('实际回答', row.actualAnswer)">回答</el-button>
            <el-button link @click="openJsonDialog('实际工具', row.actualTools)">工具</el-button>
            <el-button link type="warning" @click="openJsonDialog('RAG结果', row.ragResultsJson)">RAG</el-button>
            <el-button link type="success" @click="openJsonDialog('Judge详情', row.judgeDetailJson)">Judge</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination-row">
        <el-pagination
          v-model:current-page="resultQuery.pageNum"
          v-model:page-size="resultQuery.pageSize"
          :total="resultTotal"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          @size-change="loadResults"
          @current-change="loadResults"
        />
      </div>
    </section>

    <el-dialog v-model="datasetDialogVisible" :title="datasetForm.id ? '编辑数据集' : '新增数据集'" width="620px">
      <el-form :model="datasetForm" label-width="110px">
        <el-form-item label="名称"><el-input v-model.trim="datasetForm.datasetName" /></el-form-item>
        <el-form-item label="编码"><el-input v-model.trim="datasetForm.datasetCode" /></el-form-item>
        <el-form-item label="默认类型">
          <el-select v-model="datasetForm.evalType">
            <el-option v-for="item in evalTypes" :key="item" :label="item" :value="item" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="datasetEnabled" active-text="启用" inactive-text="禁用" />
        </el-form-item>
        <el-form-item label="说明"><el-input v-model.trim="datasetForm.description" type="textarea" :rows="3" /></el-form-item>
        <el-form-item label="备注"><el-input v-model.trim="datasetForm.remark" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="datasetDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="savingDataset" @click="submitDataset">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="caseDialogVisible" :title="caseForm.id ? '编辑用例' : '新增用例'" width="900px">
      <el-form :model="caseForm" label-width="130px">
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="用例名称"><el-input v-model.trim="caseForm.caseName" /></el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="数据集">
              <el-select v-model="caseForm.datasetId" clearable>
                <el-option v-for="item in datasetOptions" :key="item.id" :label="item.datasetName" :value="item.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="测试用户ID"><el-input-number v-model="caseForm.userId" :min="1" style="width: 100%" /></el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="评测类型">
              <el-select v-model="caseForm.evalType">
                <el-option v-for="item in evalTypes" :key="item" :label="item" :value="item" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="用户输入"><el-input v-model.trim="caseForm.inputMessage" type="textarea" :rows="3" /></el-form-item>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="期望意图"><el-input v-model.trim="caseForm.expectedIntent" /></el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="期望工具"><el-input v-model.trim="caseForm.expectedToolName" /></el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="期望参数JSON"><el-input v-model.trim="caseForm.expectedToolParamsJson" type="textarea" :rows="3" /></el-form-item>
        <el-row :gutter="12">
          <el-col :span="8">
            <el-form-item label="RAG文档ID"><el-input-number v-model="caseForm.expectedRagDocumentId" :min="1" clearable style="width: 100%" /></el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="RAG切片ID"><el-input-number v-model="caseForm.expectedRagChunkId" :min="1" clearable style="width: 100%" /></el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="最低回答分"><el-input-number v-model="caseForm.minAnswerScore" :min="0" :max="100" style="width: 100%" /></el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="RAG关键词"><el-input v-model.trim="caseForm.expectedRagKeywords" placeholder="多个关键词用英文逗号分隔" /></el-form-item>
        <el-form-item label="答案关键词"><el-input v-model.trim="caseForm.expectedAnswerKeywords" placeholder="多个关键词用英文逗号分隔" /></el-form-item>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="标签"><el-input v-model.trim="caseForm.tags" /></el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="状态"><el-switch v-model="caseEnabled" active-text="启用" inactive-text="禁用" /></el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="备注"><el-input v-model.trim="caseForm.remark" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="caseDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="savingCase" @click="submitCase">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="jsonDialogVisible" :title="jsonDialogTitle" width="860px">
      <pre class="json-preview">{{ jsonDialogContent || "-" }}</pre>
    </el-dialog>
  </main>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import {
  deleteEvalCase,
  deleteEvalDataset,
  listEnabledEvalDatasets,
  pageEvalCases,
  pageEvalDatasets,
  pageEvalResults,
  pageEvalRuns,
  runAllEvalCases,
  runEvalCase,
  runEvalDataset,
  saveEvalCase,
  saveEvalDataset,
  setEvalBaseline
} from "../../api/agentEval";
import type {
  AgentEvalCaseInfo,
  AgentEvalCaseQuery,
  AgentEvalDatasetInfo,
  AgentEvalDatasetQuery,
  AgentEvalResultInfo,
  AgentEvalResultQuery,
  AgentEvalRunInfo,
  AgentEvalRunQuery
} from "../../api/types";

const evalTypes = ["END_TO_END", "TOOL_CALL", "RAG_RETRIEVAL", "ANSWER_QUALITY"];

const datasets = ref<AgentEvalDatasetInfo[]>([]);
const datasetOptions = ref<AgentEvalDatasetInfo[]>([]);
const cases = ref<AgentEvalCaseInfo[]>([]);
const runs = ref<AgentEvalRunInfo[]>([]);
const results = ref<AgentEvalResultInfo[]>([]);
const caseTotal = ref(0);
const resultTotal = ref(0);

const loadingDatasets = ref(false);
const loadingCases = ref(false);
const loadingRuns = ref(false);
const loadingResults = ref(false);
const running = ref(false);
const savingDataset = ref(false);
const savingCase = ref(false);

const datasetDialogVisible = ref(false);
const caseDialogVisible = ref(false);
const jsonDialogVisible = ref(false);
const jsonDialogTitle = ref("");
const jsonDialogContent = ref("");

const datasetQuery = reactive<AgentEvalDatasetQuery>({ pageNum: 1, pageSize: 20 });
const caseQuery = reactive<AgentEvalCaseQuery>({ pageNum: 1, pageSize: 10 });
const runQuery = reactive<AgentEvalRunQuery>({ pageNum: 1, pageSize: 10 });
const resultQuery = reactive<AgentEvalResultQuery>({ pageNum: 1, pageSize: 10 });

const datasetForm = reactive<AgentEvalDatasetInfo>(emptyDatasetForm());
const caseForm = reactive<AgentEvalCaseInfo>(emptyCaseForm());

const latestRun = computed(() => runs.value[0]);
const latestPassRate = computed(() => {
  const run = latestRun.value;
  if (!run || !run.totalCount) return 0;
  return (run.passCount * 100) / run.totalCount;
});
const latestFailureStats = computed(() => parseFailureStats(latestRun.value?.failureStatsJson));
const datasetEnabled = computed({
  get: () => datasetForm.enableStatus !== 0,
  set: value => {
    datasetForm.enableStatus = value ? 1 : 0;
  }
});
const caseEnabled = computed({
  get: () => caseForm.enableStatus !== 0,
  set: value => {
    caseForm.enableStatus = value ? 1 : 0;
  }
});

function emptyDatasetForm(): AgentEvalDatasetInfo {
  return { datasetName: "", datasetCode: "", evalType: "END_TO_END", enableStatus: 1 };
}

function emptyCaseForm(): AgentEvalCaseInfo {
  return {
    caseName: "",
    userId: 1,
    inputMessage: "",
    evalType: "END_TO_END",
    enableStatus: 1
  };
}

async function loadDatasets() {
  loadingDatasets.value = true;
  try {
    const page = await pageEvalDatasets(datasetQuery);
    datasets.value = page.records || [];
    datasetOptions.value = await listEnabledEvalDatasets();
  } finally {
    loadingDatasets.value = false;
  }
}

async function loadCases() {
  loadingCases.value = true;
  try {
    const page = await pageEvalCases(caseQuery);
    cases.value = page.records || [];
    caseTotal.value = Number(page.total || 0);
  } finally {
    loadingCases.value = false;
  }
}

async function loadRuns() {
  loadingRuns.value = true;
  try {
    const page = await pageEvalRuns(runQuery);
    runs.value = page.records || [];
  } finally {
    loadingRuns.value = false;
  }
}

async function loadResults() {
  loadingResults.value = true;
  try {
    const page = await pageEvalResults(resultQuery);
    results.value = page.records || [];
    resultTotal.value = Number(page.total || 0);
  } finally {
    loadingResults.value = false;
  }
}

async function reloadAll() {
  await Promise.all([loadDatasets(), loadCases(), loadRuns(), loadResults()]);
}

function openDatasetDialog(row?: AgentEvalDatasetInfo) {
  Object.assign(datasetForm, emptyDatasetForm(), row || {});
  datasetDialogVisible.value = true;
}

function openCaseDialog(row?: AgentEvalCaseInfo) {
  Object.assign(caseForm, emptyCaseForm(), row || {});
  caseDialogVisible.value = true;
}

async function submitDataset() {
  savingDataset.value = true;
  try {
    await saveEvalDataset(datasetForm);
    ElMessage.success("数据集已保存");
    datasetDialogVisible.value = false;
    await loadDatasets();
  } finally {
    savingDataset.value = false;
  }
}

async function submitCase() {
  savingCase.value = true;
  try {
    // 1. 参数 JSON 是评测断言的一部分，保存前先做基础校验，避免运行时才发现格式错误。
    validateJsonText(caseForm.expectedToolParamsJson);
    await saveEvalCase(caseForm);
    ElMessage.success("用例已保存");
    caseDialogVisible.value = false;
    await loadCases();
  } finally {
    savingCase.value = false;
  }
}

async function removeDataset(row: AgentEvalDatasetInfo) {
  await ElMessageBox.confirm(`确认删除数据集「${row.datasetName}」？`, "删除确认", { type: "warning" });
  await deleteEvalDataset(row.id!);
  ElMessage.success("数据集已删除");
  await loadDatasets();
}

async function removeCase(row: AgentEvalCaseInfo) {
  await ElMessageBox.confirm(`确认删除用例「${row.caseName}」？`, "删除确认", { type: "warning" });
  await deleteEvalCase(row.id!);
  ElMessage.success("用例已删除");
  await loadCases();
}

async function runCase(row: AgentEvalCaseInfo) {
  running.value = true;
  try {
    await runEvalCase(row.id!);
    ElMessage.success("用例运行完成");
    await Promise.all([loadRuns(), loadResults()]);
  } finally {
    running.value = false;
  }
}

async function runDataset(row: AgentEvalDatasetInfo) {
  running.value = true;
  try {
    await runEvalDataset(row.id!);
    ElMessage.success("数据集回归完成");
    await Promise.all([loadRuns(), loadResults()]);
  } finally {
    running.value = false;
  }
}

async function runAll() {
  running.value = true;
  try {
    await runAllEvalCases();
    ElMessage.success("全量回归完成");
    await Promise.all([loadRuns(), loadResults()]);
  } finally {
    running.value = false;
  }
}

async function markBaseline(row: AgentEvalRunInfo) {
  await ElMessageBox.confirm(`确认把运行批次 #${row.id} 设为基准？同范围旧基准会被替换。`, "设置基准", { type: "warning" });
  await setEvalBaseline(row.id);
  ElMessage.success("基准批次已更新");
  await loadRuns();
}

function filterCasesByDataset(row: AgentEvalDatasetInfo) {
  caseQuery.datasetId = row.id;
  caseQuery.pageNum = 1;
  loadCases();
}

function filterResultsByRun(row: AgentEvalRunInfo) {
  resultQuery.runId = row.id;
  resultQuery.pageNum = 1;
  loadResults();
}

function searchCases() {
  caseQuery.pageNum = 1;
  loadCases();
}

function resetCaseQuery() {
  Object.assign(caseQuery, { pageNum: 1, pageSize: 10, datasetId: "", caseName: "", evalType: "", expectedToolName: "" });
  loadCases();
}

function openJsonDialog(title: string, content?: string) {
  jsonDialogTitle.value = title;
  jsonDialogContent.value = formatJson(content);
  jsonDialogVisible.value = true;
}

function validateJsonText(text?: string) {
  if (!text) return;
  try {
    JSON.parse(text);
  } catch {
    throw new Error("期望参数 JSON 格式不正确");
  }
}

function formatJson(text?: string) {
  if (!text) return "";
  try {
    return JSON.stringify(JSON.parse(text), null, 2);
  } catch {
    return text;
  }
}

function formatPass(value?: number | null) {
  if (value === undefined || value === null) return "-";
  return value === 1 ? "通过" : "失败";
}

function formatMetric(value?: number) {
  return value === undefined || value === null ? "-" : `${value}%`;
}

function formatPercent(value: number) {
  return `${value.toFixed(2)}%`;
}

function formatDelta(value?: number, percent = true) {
  if (value === undefined || value === null) return "无基准对比";
  const sign = value > 0 ? "+" : "";
  return `${sign}${value}${percent ? "%" : ""}`;
}

function parseFailureStats(text?: string) {
  if (!text) return [];
  try {
    const data = JSON.parse(text) as Record<string, number>;
    return Object.entries(data).map(([name, count]) => ({ name, count }));
  } catch {
    return [];
  }
}

onMounted(reloadAll);
</script>

<style scoped>
.header-actions,
.section-title-row {
  display: flex;
  align-items: center;
  gap: 10px;
}

.header-actions {
  justify-content: flex-end;
}

.section-title-row {
  justify-content: space-between;
  margin-bottom: 14px;
}

.section-title-row h2 {
  margin: 0;
  font-size: 18px;
}

.section-title-row p {
  margin: 4px 0 0;
  color: #6b7280;
}

.metric-grid {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 16px;
}

.metric-card {
  padding: 14px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #fff;
}

.metric-card span {
  display: block;
  color: #6b7280;
  font-size: 13px;
}

.metric-card strong {
  display: block;
  margin-top: 8px;
  font-size: 24px;
}

.compact-filter {
  margin-bottom: 12px;
}

.json-preview {
  max-height: 520px;
  overflow: auto;
  padding: 12px;
  border-radius: 6px;
  background: #111827;
  color: #e5e7eb;
  white-space: pre-wrap;
}

@media (max-width: 1200px) {
  .metric-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
