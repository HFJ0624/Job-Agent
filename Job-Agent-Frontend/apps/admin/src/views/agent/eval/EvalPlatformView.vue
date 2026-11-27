<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import {
  createEvalCoreTemplates,
  getEvalBatchReport,
  getEvalOverview,
  pageEvalCases,
  pageEvalRuns,
  runEvalDataset,
  saveEvalCase,
  saveEvalDataset,
  setEvalBaseline
} from "../../../api/agentEval";
import { listEnabledEvalDatasets } from "../../../api/agentEval";
import { useAdminUserStore } from "../../../stores/user";
import type {
  AgentEvalCaseInfo,
  AgentEvalDatasetInfo,
  AgentEvalResultInfo,
  AgentEvalRunInfo,
  EvalBatchReportInfo,
  EvalOverviewInfo
} from "../../../api/types";

/**
 * 功能: 评测平台（重做版）。
 *
 * 设计原则：面向非技术用户——标题说"检查 AI 有没有变差"，按钮说"跑一次评测"，
 * 指标卡带一句人话解释；技术细节（traceId、失败码）收在批次报告里，不打扰主流程。
 */

type TabKey = "overview" | "cases" | "batches";

const activeTab = ref<TabKey>("overview");
const datasets = ref<AgentEvalDatasetInfo[]>([]);
const datasetId = ref<number | undefined>();

const overview = ref<EvalOverviewInfo | null>(null);
const overviewLoading = ref(false);

const cases = ref<AgentEvalCaseInfo[]>([]);
const caseTotal = ref(0);
const casePage = ref(1);
const casesLoading = ref(false);

const batches = ref<AgentEvalRunInfo[]>([]);
const batchTotal = ref(0);
const batchPage = ref(1);
const batchesLoading = ref(false);

const running = ref(false);
const reportVisible = ref(false);
const reportLoading = ref(false);
const report = ref<EvalBatchReportInfo | null>(null);

/* ---------- 建题库 / 加题目（原来页面缺的入口） ---------- */
const userStore = useAdminUserStore();
const datasetDialog = ref(false);
const datasetSaving = ref(false);
const datasetForm = ref({ datasetName: "", datasetCode: "", description: "" });

const caseDialog = ref(false);
const caseSaving = ref(false);
const caseForm = ref({
  caseName: "",
  inputMessage: "",
  expectedToolName: "",
  expectedToolParamsJson: "",
  expectedAnswerKeywords: "",
  tags: ""
});

const latest = computed(() => overview.value?.latestRun ?? null);
const hasRun = computed(() => !!latest.value);
const passRate = computed(() => Number(latest.value?.passCount && latest.value?.totalCount
  ? (latest.value.passCount / latest.value.totalCount) * 100
  : 0).toFixed(1));
const failCount = computed(() => latest.value?.failCount ?? 0);

/** 指标卡配置：数值 + 人话解释 + 环比（正数绿色、负数红色）。 */
const metricCards = computed(() => [
  {
    key: "pass",
    title: "通过率",
    value: latest.value ? `${passRate.value}%` : "--",
    hint: latest.value ? `${latest.value.totalCount} 道题里过了 ${latest.value.passCount} 道` : "还没有跑过评测",
    delta: null as number | null,
    tone: "primary"
  },
  {
    key: "tool",
    title: "工具选对率",
    value: latest.value?.toolAccuracy != null ? `${Number(latest.value.toolAccuracy).toFixed(1)}%` : "--",
    hint: "该调用哪个工具，选对了吗",
    delta: latest.value?.toolAccuracyDelta ?? null,
    tone: "blue"
  },
  {
    key: "param",
    title: "条件理解准确率",
    value: latest.value?.paramAccuracy != null ? `${Number(latest.value.paramAccuracy).toFixed(1)}%` : "--",
    hint: "城市、关键词这些条件提取对了吗",
    delta: latest.value?.paramAccuracyDelta ?? null,
    tone: "violet"
  },
  {
    key: "rag",
    title: "资料命中率",
    value: latest.value?.ragHitRate != null ? `${Number(latest.value.ragHitRate).toFixed(1)}%` : "--",
    hint: "回答前有没有找到正确的资料",
    delta: latest.value?.ragHitRateDelta ?? null,
    tone: "teal"
  }
]);

/** 趋势条高度：最低 8%，避免 0 通过率看不见。 */
function barHeight(rate?: number) {
  const value = Number(rate ?? 0);
  return `${Math.max(8, Math.min(100, value))}%`;
}

function formatTime(value?: string) {
  if (!value) return "—";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return `${date.getMonth() + 1}月${date.getDate()}日 ${String(date.getHours()).padStart(2, "0")}:${String(date.getMinutes()).padStart(2, "0")}`;
}

function statusText(status?: string) {
  const map: Record<string, string> = { SUCCESS: "已完成", COMPLETED: "已完成", RUNNING: "进行中", FAILED: "失败", PENDING: "排队中" };
  return map[status ?? ""] ?? status ?? "未知";
}

function statusTone(status?: string) {
  if (status === "SUCCESS" || status === "COMPLETED") return "success";
  if (status === "RUNNING" || status === "PENDING") return "warning";
  return "danger";
}

async function loadDatasets() {
  datasets.value = await listEnabledEvalDatasets();
  if (!datasetId.value && datasets.value.length) {
    datasetId.value = datasets.value[0].id;
  }
}

async function loadOverview() {
  overviewLoading.value = true;
  try {
    overview.value = await getEvalOverview(datasetId.value);
  } finally {
    overviewLoading.value = false;
  }
}

async function loadCases() {
  casesLoading.value = true;
  try {
    const page = await pageEvalCases({ pageNum: casePage.value, pageSize: 10, datasetId: datasetId.value });
    cases.value = page.records ?? [];
    caseTotal.value = Number(page.total ?? 0);
  } finally {
    casesLoading.value = false;
  }
}

async function loadBatches() {
  batchesLoading.value = true;
  try {
    const page = await pageEvalRuns({ pageNum: batchPage.value, pageSize: 10, datasetId: datasetId.value });
    batches.value = page.records ?? [];
    batchTotal.value = Number(page.total ?? 0);
  } finally {
    batchesLoading.value = false;
  }
}

async function refreshAll() {
  await Promise.all([loadOverview(), loadCases(), loadBatches()]);
}

async function switchDataset() {
  casePage.value = 1;
  batchPage.value = 1;
  await refreshAll();
}

/** 跑一次整库评测：后端异步执行，这里只负责提示 + 刷新列表。 */
async function runEvaluation() {
  if (!datasetId.value) {
    ElMessage.warning("先选择一个题库（数据集）");
    return;
  }
  running.value = true;
  try {
    await runEvalDataset(datasetId.value);
    ElMessage.success("评测已开始，完成后可在「历史批次」查看报告");
    activeTab.value = "batches";
    await refreshAll();
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : "启动评测失败");
  } finally {
    running.value = false;
  }
}

async function openReport(runId: number) {
  reportVisible.value = true;
  reportLoading.value = true;
  report.value = null;
  try {
    report.value = await getEvalBatchReport(runId);
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : "报告加载失败");
  } finally {
    reportLoading.value = false;
  }
}

/** 新建题库：建完自动选中并刷新题目列表。 */
async function submitDataset() {
  if (!datasetForm.value.datasetName.trim()) {
    ElMessage.warning("给题库起个名字");
    return;
  }
  if (!datasetForm.value.datasetCode.trim()) {
    ElMessage.warning("编码不能为空（建议 core_v1 这种带版本的写法）");
    return;
  }
  datasetSaving.value = true;
  try {
    const created = await saveEvalDataset({
      datasetName: datasetForm.value.datasetName.trim(),
      datasetCode: datasetForm.value.datasetCode.trim(),
      description: datasetForm.value.description.trim(),
      evalType: "CALL",
      enableStatus: 1
    } as never);
    datasetDialog.value = false;
    datasetForm.value = { datasetName: "", datasetCode: "", description: "" };
    await loadDatasets();
    datasetId.value = created?.id ?? datasetId.value;
    activeTab.value = "cases";
    await refreshAll();
    ElMessage.success("题库已创建，接着点「添加题目」或「一键生成核心模板」");
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : "创建题库失败");
  } finally {
    datasetSaving.value = false;
  }
}

/** 一键生成核心链路模板题（覆盖 4 条链路的基础题型）。 */
async function generateCoreTemplates() {
  if (!datasetId.value) {
    ElMessage.warning("先选择题库");
    return;
  }
  try {
    await ElMessageBox.confirm(
      "会在当前题库生成一批核心链路模板题（不会覆盖已有题目）。适合第一次建库时打底。",
      "一键生成核心模板",
      { confirmButtonText: "生成", cancelButtonText: "取消", type: "info" }
    );
  } catch {
    return;
  }
  try {
    const result = await createEvalCoreTemplates({
      datasetId: datasetId.value,
      userId: Number(userStore.profile?.id ?? 1),
      overwrite: false
    });
    ElMessage.success(`已生成 ${result.createdCount} 道题，跳过 ${result.skippedCount} 道`);
    await refreshAll();
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : "生成失败");
  }
}

/** 手工添加一道题：最少填 名称 + 用户输入 + 期望工具。 */
async function submitCase() {
  if (!datasetId.value) {
    ElMessage.warning("先选择题库");
    return;
  }
  if (!caseForm.value.caseName.trim() || !caseForm.value.inputMessage.trim()) {
    ElMessage.warning("题目名称和用户输入必填");
    return;
  }
  caseSaving.value = true;
  try {
    await saveEvalCase({
      datasetId: datasetId.value,
      caseName: caseForm.value.caseName.trim(),
      inputMessage: caseForm.value.inputMessage.trim(),
      evalType: "TOOL",
      expectedToolName: caseForm.value.expectedToolName.trim() || undefined,
      expectedToolParamsJson: caseForm.value.expectedToolParamsJson.trim() || undefined,
      expectedAnswerKeywords: caseForm.value.expectedAnswerKeywords.trim() || undefined,
      tags: caseForm.value.tags.trim() || undefined,
      enableStatus: 1
    } as never);
    caseDialog.value = false;
    caseForm.value = { caseName: "", inputMessage: "", expectedToolName: "", expectedToolParamsJson: "", expectedAnswerKeywords: "", tags: "" };
    await refreshAll();
    ElMessage.success("题目已添加");
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : "添加题目失败");
  } finally {
    caseSaving.value = false;
  }
}

async function markBaseline(runId: number) {
  await setEvalBaseline(runId);
  ElMessage.success("已设为对比基线，下次评测会自动和它比较");
  await loadBatches();
}

/** 单条结果的展示：通过/失败 + 失败原因（人话）。 */
function resultPassed(result: AgentEvalResultInfo) {
  return result.passStatus === 1;
}

function resultReason(result: AgentEvalResultInfo) {
  if (resultPassed(result)) return "全部断言通过";
  return result.failReason || "未满足预期（展开可看详情）";
}

function copyTrace(traceId?: string) {
  if (!traceId) return;
  navigator.clipboard?.writeText(traceId);
  ElMessage.success("已复制链路 ID，可去「Trace」里查这一次执行");
}

onMounted(async () => {
  await loadDatasets();
  await refreshAll();
});
</script>

<template>
  <div class="eval-page">
    <header class="eval-hero">
      <div>
        <h1>评测平台</h1>
        <p>用一批标准题目，检查 AI 求职助手是否"变差了"。每次评测就是一次考试，报告告诉你哪类题没做好。</p>
      </div>
      <div class="hero-actions">
        <el-select v-model="datasetId" placeholder="选择题库" style="width: 200px" @change="switchDataset">
          <el-option v-for="item in datasets" :key="item.id" :label="item.datasetName" :value="item.id" />
        </el-select>
        <el-button type="primary" :loading="running" @click="runEvaluation">跑一次评测</el-button>
      </div>
    </header>

    <nav class="eval-tabs">
      <button :class="{ active: activeTab === 'overview' }" @click="activeTab = 'overview'">概览</button>
      <button :class="{ active: activeTab === 'cases' }" @click="activeTab = 'cases'">题目库</button>
      <button :class="{ active: activeTab === 'batches' }" @click="activeTab = 'batches'">历史批次</button>
    </nav>

    <!-- 概览 -->
    <section v-if="activeTab === 'overview'" v-loading="overviewLoading" class="eval-section">
      <div class="metric-grid">
        <article v-for="card in metricCards" :key="card.key" class="metric-card" :class="`tone-${card.tone}`">
          <span class="metric-title">{{ card.title }}</span>
          <strong class="metric-value">{{ card.value }}</strong>
          <span class="metric-hint">{{ card.hint }}</span>
          <span v-if="card.delta != null" class="metric-delta" :class="Number(card.delta) >= 0 ? 'up' : 'down'">
            {{ Number(card.delta) >= 0 ? "↑" : "↓" }} {{ Math.abs(Number(card.delta)).toFixed(1) }} 对比上次
          </span>
        </article>
      </div>

      <div v-if="!hasRun" class="empty-card">
        <div class="empty-illustration">🧪</div>
        <h3>还没有跑过评测</h3>
        <p>点右上角「跑一次评测」，系统会用题库里的题逐条检查 AI，并生成一份可对比的报告。</p>
        <el-button type="primary" :loading="running" @click="runEvaluation">开始第一次评测</el-button>
      </div>

      <div v-else class="two-column">
        <article class="panel">
          <header class="panel-head">
            <h3>通过率趋势</h3>
            <span>最近 {{ overview?.trend?.length ?? 0 }} 次</span>
          </header>
          <div class="trend">
            <div v-for="point in overview?.trend" :key="point.runId" class="trend-item">
              <div class="trend-bar" :style="{ height: barHeight(point.passRate) }">
                <span>{{ Number(point.passRate ?? 0).toFixed(0) }}%</span>
              </div>
              <small>{{ formatTime(point.time) }}</small>
            </div>
          </div>
        </article>

        <article class="panel">
          <header class="panel-head">
            <h3>主要失分点</h3>
            <span>{{ failCount }} 道题未通过</span>
          </header>
          <ul v-if="overview?.failureTop?.length" class="failure-list">
            <li v-for="item in overview.failureTop" :key="item.code">
              <div class="failure-row">
                <span>{{ item.label }}</span>
                <b>{{ item.count }} 道</b>
              </div>
              <div class="failure-track">
                <div class="failure-fill" :style="{ width: `${Math.min(100, Number(item.ratio ?? 0))}%` }" />
              </div>
            </li>
          </ul>
          <p v-else class="muted">这次没有失败题目，保持住。</p>
        </article>
      </div>

      <article v-if="hasRun" class="panel">
        <header class="panel-head">
          <h3>这次评测说了什么</h3>
          <span>{{ formatTime(latest?.endTime || latest?.startTime) }}</span>
        </header>
        <p class="summary">
          共 {{ latest?.totalCount ?? 0 }} 道题，通过 {{ latest?.passCount ?? 0 }} 道；
          工具选择 {{ Number(latest?.toolAccuracy ?? 0).toFixed(1) }}%，
          条件提取 {{ Number(latest?.paramAccuracy ?? 0).toFixed(1) }}%，
          资料命中 {{ Number(latest?.ragHitRate ?? 0).toFixed(1) }}%。
          平均每题耗时 {{ latest?.avgCostTime ?? 0 }} ms。
        </p>
        <el-button text type="primary" @click="openReport(latest!.id)">查看完整报告 →</el-button>
      </article>
    </section>

    <!-- 题目库 -->
    <section v-else-if="activeTab === 'cases'" v-loading="casesLoading" class="eval-section">
      <article class="panel">
        <header class="panel-head">
          <div>
            <h3>题目库</h3>
            <span>共 {{ caseTotal }} 道题；题目越贴近真实用户，评测越可信</span>
          </div>
          <div class="toolbar">
            <el-button @click="datasetDialog = true">新建题库</el-button>
            <el-button @click="generateCoreTemplates">一键生成核心模板</el-button>
            <el-button type="primary" @click="caseDialog = true">添加题目</el-button>
          </div>
        </header>
        <el-table :data="cases" size="large" empty-text="这个题库还没有题目">
          <el-table-column prop="caseName" label="题目" min-width="160" />
          <el-table-column prop="inputMessage" label="用户会怎么说" min-width="260" show-overflow-tooltip />
          <el-table-column prop="expectedToolName" label="期望调用的工具" min-width="180">
            <template #default="{ row }">
              <el-tag effect="plain" round>{{ row.expectedToolName || "—" }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="evalType" label="类型" width="130" />
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="row.enableStatus === 1 ? 'success' : 'info'" effect="light" round>
                {{ row.enableStatus === 1 ? "启用" : "停用" }}
              </el-tag>
            </template>
          </el-table-column>
        </el-table>
        <div class="pager">
          <el-pagination
            v-model:current-page="casePage"
            :page-size="10"
            :total="caseTotal"
            layout="prev, pager, next"
            @current-change="loadCases"
          />
        </div>
      </article>
    </section>

    <!-- 历史批次 -->
    <section v-else class="eval-section">
      <article class="panel">
        <header class="panel-head">
          <h3>历史批次</h3>
          <span>每一行 = 一次完整考试；可设一次为基线，后续自动对比</span>
        </header>
        <el-table v-loading="batchesLoading" :data="batches" size="large" empty-text="还没有评测记录">
          <el-table-column label="时间" width="150">
            <template #default="{ row }">{{ formatTime(row.startTime || row.createTime) }}</template>
          </el-table-column>
          <el-table-column prop="runName" label="批次" min-width="180" show-overflow-tooltip />
          <el-table-column label="题数" width="80">
            <template #default="{ row }">{{ row.totalCount }}</template>
          </el-table-column>
          <el-table-column label="通过率" width="180">
            <template #default="{ row }">
              <div class="rate-cell">
                <el-progress
                  :percentage="row.totalCount ? Math.round((row.passCount / row.totalCount) * 100) : 0"
                  :stroke-width="8"
                  :show-text="false"
                />
                <span>{{ row.totalCount ? Math.round((row.passCount / row.totalCount) * 100) : 0 }}%</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="statusTone(row.status)" effect="light" round>{{ statusText(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="200" fixed="right">
            <template #default="{ row }">
              <el-button text type="primary" @click="openReport(row.id)">看报告</el-button>
              <el-button text @click="markBaseline(row.id)">设为基线</el-button>
            </template>
          </el-table-column>
        </el-table>
        <div class="pager">
          <el-pagination
            v-model:current-page="batchPage"
            :page-size="10"
            :total="batchTotal"
            layout="prev, pager, next"
            @current-change="loadBatches"
          />
        </div>
      </article>
    </section>

    <!-- 新建题库 -->
    <el-dialog v-model="datasetDialog" title="新建题库" width="460px">
      <el-form label-width="90px">
        <el-form-item label="题库名称" required>
          <el-input v-model="datasetForm.datasetName" placeholder="例如：核心链路回归" />
        </el-form-item>
        <el-form-item label="题库编码" required>
          <el-input v-model="datasetForm.datasetCode" placeholder="例如：core_v1（带版本，便于以后冻结）" />
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="datasetForm.description" type="textarea" :rows="3" placeholder="这套题考什么、谁维护" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="datasetDialog = false">取消</el-button>
        <el-button type="primary" :loading="datasetSaving" @click="submitDataset">创建</el-button>
      </template>
    </el-dialog>

    <!-- 添加题目 -->
    <el-dialog v-model="caseDialog" title="添加题目" width="620px">
      <el-form label-width="110px">
        <el-form-item label="题目名称" required>
          <el-input v-model="caseForm.caseName" placeholder="例如：上海 Java 岗位搜索" />
        </el-form-item>
        <el-form-item label="用户会怎么说" required>
          <el-input v-model="caseForm.inputMessage" type="textarea" :rows="2"
                    placeholder="例如：帮我在上海找 Java 岗位，15k 以上" />
        </el-form-item>
        <el-form-item label="期望工具">
          <el-input v-model="caseForm.expectedToolName" placeholder="例如：JobSearchTool.searchJobs" />
        </el-form-item>
        <el-form-item label="期望参数">
          <el-input v-model="caseForm.expectedToolParamsJson" type="textarea" :rows="2"
                    placeholder='JSON，例如 {"city":"上海","keyword":"Java"}' />
        </el-form-item>
        <el-form-item label="答案关键词">
          <el-input v-model="caseForm.expectedAnswerKeywords" placeholder="逗号分隔，例如：上海,Java" />
        </el-form-item>
        <el-form-item label="标签">
          <el-input v-model="caseForm.tags" placeholder="例如：core,search" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="caseDialog = false">取消</el-button>
        <el-button type="primary" :loading="caseSaving" @click="submitCase">保存</el-button>
      </template>
    </el-dialog>

    <!-- 批次报告抽屉 -->
    <el-drawer v-model="reportVisible" size="60%" :with-header="false">
      <div v-loading="reportLoading" class="report">
        <template v-if="report?.run">
          <header class="report-head">
            <div>
              <h2>{{ report.run.runName || "评测报告" }}</h2>
              <p>{{ formatTime(report.run.startTime || report.run.createTime) }} · {{ report.run.totalCount }} 道题</p>
            </div>
            <div class="report-tags">
              <el-tag :type="statusTone(report.run.status)" effect="light" round>{{ statusText(report.run.status) }}</el-tag>
              <!-- 门禁徽标：练习 3 实现后自动出现 -->
              <el-tag v-if="report.gatePass !== undefined && report.gatePass !== null"
                      :type="report.gatePass ? 'success' : 'danger'" effect="dark" round>
                {{ report.gatePass ? "PASS 可合入" : "BLOCK 需修复" }}
              </el-tag>
            </div>
          </header>

          <div class="report-metrics">
            <div><span>通过</span><strong>{{ report.run.passCount }}</strong></div>
            <div><span>未通过</span><strong>{{ report.run.failCount }}</strong></div>
            <div><span>工具准确率</span><strong>{{ Number(report.run.toolAccuracy ?? 0).toFixed(1) }}%</strong></div>
            <div><span>条件准确率</span><strong>{{ Number(report.run.paramAccuracy ?? 0).toFixed(1) }}%</strong></div>
            <div><span>资料命中率</span><strong>{{ Number(report.run.ragHitRate ?? 0).toFixed(1) }}%</strong></div>
            <div><span>平均耗时</span><strong>{{ report.run.avgCostTime ?? 0 }}ms</strong></div>
          </div>

          <p v-if="report.gateReason" class="gate-reason" :class="{ blocked: report.gatePass === false }">
            {{ report.gateReason }}
          </p>

          <h3 class="report-subtitle">失分点分布</h3>
          <div v-if="report.failureCategories.length" class="chip-row">
            <span v-for="item in report.failureCategories" :key="item.code" class="failure-chip">
              {{ item.label }} · {{ item.count }}
            </span>
          </div>
          <p v-else class="muted">本批次没有失败用例。</p>

          <h3 class="report-subtitle">逐题结果</h3>
          <el-table :data="report.cases" size="small" max-height="420">
            <el-table-column label="结果" width="80">
              <template #default="{ row }">
                <el-tag :type="resultPassed(row) ? 'success' : 'danger'" effect="light" round>
                  {{ resultPassed(row) ? "通过" : "未过" }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="inputMessage" label="用户输入" min-width="200" show-overflow-tooltip />
            <el-table-column label="原因" min-width="200" show-overflow-tooltip>
              <template #default="{ row }">{{ resultReason(row) }}</template>
            </el-table-column>
            <el-table-column label="耗时" width="90">
              <template #default="{ row }">{{ row.costTime ?? 0 }}ms</template>
            </el-table-column>
            <el-table-column label="链路" width="90">
              <template #default="{ row }">
                <el-button v-if="row.traceId" text type="primary" @click="copyTrace(row.traceId)">复制 ID</el-button>
                <span v-else class="muted">—</span>
              </template>
            </el-table-column>
          </el-table>
        </template>
      </div>
    </el-drawer>
  </div>
</template>

<style scoped>
/* 与 RAG 知识库一致：铺满主内容区，不再限宽居中 */
.eval-page {
  width: 100%;
  padding: 4px 0 24px;
}

.eval-hero {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 24px;
  margin-bottom: 18px;
}

.eval-hero h1 {
  margin: 0 0 6px;
  font-size: 26px;
  letter-spacing: -0.4px;
}

.eval-hero p {
  margin: 0;
  max-width: 640px;
  color: var(--admin-muted);
  line-height: 1.6;
}

.hero-actions {
  display: flex;
  gap: 10px;
  align-items: center;
}

.eval-tabs {
  display: inline-flex;
  gap: 4px;
  padding: 4px;
  background: #fff;
  border: 1px solid #e5e9f2;
  border-radius: 12px;
  margin-bottom: 18px;
}

.eval-tabs button {
  border: 0;
  background: transparent;
  padding: 8px 18px;
  border-radius: 9px;
  cursor: pointer;
  color: #4b5563;
  font-size: 14px;
  transition: 0.15s ease;
}

.eval-tabs button.active {
  background: #eef4ff;
  color: #2563eb;
  font-weight: 600;
}

.metric-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(220px, 1fr));
  gap: 14px;
  margin-bottom: 16px;
}

.metric-card {
  background: #fff;
  border: 1px solid #e9edf5;
  border-radius: 16px;
  padding: 18px 18px 16px;
  display: flex;
  flex-direction: column;
  gap: 6px;
  box-shadow: 0 6px 18px rgba(15, 23, 42, 0.04);
}

.metric-title {
  font-size: 13px;
  color: #6b7280;
}

.metric-value {
  font-size: 30px;
  letter-spacing: -0.6px;
}

.metric-hint {
  font-size: 12px;
  color: #9aa3b2;
  line-height: 1.5;
}

.metric-delta {
  font-size: 12px;
  font-weight: 600;
}

.metric-delta.up {
  color: #16a34a;
}

.metric-delta.down {
  color: #dc2626;
}

.tone-primary { border-top: 3px solid #3b82f6; }
.tone-blue { border-top: 3px solid #0ea5e9; }
.tone-violet { border-top: 3px solid #8b5cf6; }
.tone-teal { border-top: 3px solid #14b8a6; }

.two-column {
  display: grid;
  grid-template-columns: minmax(0, 1.5fr) minmax(320px, 1fr);
  gap: 14px;
  margin-bottom: 16px;
}

.panel {
  background: #fff;
  border: 1px solid #e9edf5;
  border-radius: 16px;
  padding: 18px;
  box-shadow: 0 6px 18px rgba(15, 23, 42, 0.04);
}

.panel-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-bottom: 14px;
}

.panel-head h3 {
  margin: 0;
  font-size: 15px;
}

.panel-head span {
  font-size: 12px;
  color: #9aa3b2;
}

.trend {
  display: flex;
  align-items: flex-end;
  gap: 12px;
  height: 180px;
  padding: 8px 4px 0;
}

.trend-item {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  height: 100%;
  justify-content: flex-end;
}

.trend-bar {
  width: 100%;
  max-width: 34px;
  border-radius: 8px 8px 4px 4px;
  background: linear-gradient(180deg, #60a5fa, #3b82f6);
  display: flex;
  align-items: flex-start;
  justify-content: center;
  min-height: 10px;
}

.trend-bar span {
  color: #fff;
  font-size: 11px;
  margin-top: 4px;
}

.trend-item small {
  font-size: 11px;
  color: #9aa3b2;
  white-space: nowrap;
}

.failure-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.failure-row {
  display: flex;
  justify-content: space-between;
  font-size: 13px;
  margin-bottom: 6px;
}

.failure-track {
  height: 8px;
  background: #f2f4f8;
  border-radius: 999px;
  overflow: hidden;
}

.failure-fill {
  height: 100%;
  border-radius: 999px;
  background: linear-gradient(90deg, #f97316, #ef4444);
}

.empty-card {
  background: #fff;
  border: 1px dashed #dbe3ef;
  border-radius: 18px;
  padding: 44px 24px;
  text-align: center;
  margin-bottom: 16px;
}

.empty-illustration {
  font-size: 40px;
  margin-bottom: 8px;
}

.empty-card h3 {
  margin: 0 0 6px;
}

.empty-card p {
  margin: 0 auto 16px;
  max-width: 460px;
  color: #7b8494;
  line-height: 1.7;
}

.summary {
  margin: 0 0 8px;
  color: #4b5563;
  line-height: 1.8;
}

.muted {
  color: #9aa3b2;
  font-size: 13px;
}

.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 12px;
}

.toolbar {
  display: flex;
  gap: 8px;
  align-items: center;
}

.rate-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}

.rate-cell :deep(.el-progress) {
  flex: 1;
}

.report {
  padding: 8px 24px 24px;
}

.report-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
  margin-bottom: 16px;
}

.report-head h2 {
  margin: 0 0 4px;
  font-size: 22px;
}

.report-head p {
  margin: 0;
  color: #8b93a1;
  font-size: 13px;
}

.report-metrics {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: 10px;
  margin-bottom: 18px;
}

.report-metrics div {
  background: #f8fafc;
  border-radius: 12px;
  padding: 12px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.report-metrics span {
  font-size: 12px;
  color: #8b93a1;
}

.report-metrics strong {
  font-size: 18px;
}

.report-subtitle {
  font-size: 14px;
  margin: 18px 0 10px;
}

.report-tags {
  display: flex;
  gap: 8px;
  align-items: center;
}

.gate-reason {
  margin: 0 0 6px;
  padding: 10px 12px;
  border-radius: 10px;
  background: #f0fdf4;
  color: #15803d;
  font-size: 13px;
}

.gate-reason.blocked {
  background: #fef2f2;
  color: #b91c1c;
}

.chip-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.failure-chip {
  background: #fff7ed;
  color: #c2410c;
  border: 1px solid #fed7aa;
  border-radius: 999px;
  padding: 4px 12px;
  font-size: 12px;
}
</style>
