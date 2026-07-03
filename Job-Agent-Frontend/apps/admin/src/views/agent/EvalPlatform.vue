<template>
  <main class="admin-page">
    <section class="page-header">
      <div>
        <p class="eyebrow">Agent Eval</p>
        <h1>Eval 评测平台</h1>
        <p>管理评测数据集和用例，批量回归工具选择、参数、RAG 命中和回答质量。</p>
      </div>
      <div class="header-actions">
        <el-button type="warning" @click="openTemplateDialog">生成核心链路模板</el-button>
        <el-button :loading="running" @click="runAll">全量回归</el-button>
        <el-button type="primary" @click="openCaseDialog()">新增用例</el-button>
        <el-button type="success" @click="openDatasetDialog()">新增数据集</el-button>
      </div>
    </section>

    <section class="table-card health-card">
      <div class="section-title-row">
        <div>
          <h2>核心链路质量体检</h2>
          <p>基于最近一次回归批次，聚合工具选择、RAG、记忆、Guardrails、JSON 输出和回答质量。</p>
        </div>
        <el-button :loading="loadingHealth" @click="loadHealthReport">刷新体检</el-button>
      </div>
      <el-empty v-if="!healthReport?.latestRunId" description="暂无回归批次，请先运行核心链路数据集" />
      <template v-else>
        <div class="health-summary">
          <div>
            <span>最近批次</span>
            <strong>#{{ healthReport.latestRunId }} {{ healthReport.latestRunName || "" }}</strong>
          </div>
          <div>
            <span>整体通过率</span>
            <strong>{{ formatMetric(healthReport.passRate) }}</strong>
          </div>
          <div>
            <span>核心覆盖率</span>
            <strong>{{ formatMetric(healthReport.coreCoverageRate) }}</strong>
          </div>
          <div>
            <span>最薄弱指标</span>
            <strong>{{ healthReport.weakestMetric || "-" }}</strong>
          </div>
        </div>

        <div class="health-grid">
          <div class="health-panel">
            <h3>指标体检</h3>
            <div v-for="item in healthReport.metricItems" :key="item.metricCode" class="health-row">
              <span>{{ item.metricName }}</span>
              <strong>{{ formatHealthMetric(item) }}</strong>
              <el-tag :type="metricTagType(item.status)" effect="light">{{ item.status || "NO_DATA" }}</el-tag>
            </div>
          </div>
          <div class="health-panel">
            <h3>覆盖情况</h3>
            <p>已覆盖</p>
            <div class="tag-row">
              <el-tag v-for="item in healthReport.coveredCoreTypes" :key="item" type="success" effect="light">{{ item }}</el-tag>
            </div>
            <p>缺失</p>
            <div class="tag-row">
              <el-tag v-for="item in healthReport.missingCoreTypes" :key="item" type="warning" effect="light">{{ item }}</el-tag>
              <span v-if="!healthReport.missingCoreTypes.length" class="muted-text">核心链路已覆盖</span>
            </div>
          </div>
          <div class="health-panel">
            <h3>失败分类</h3>
            <div v-if="healthReport.failureItems.length">
              <div v-for="item in healthReport.failureItems" :key="item.failureType" class="failure-item">
                <strong>{{ item.failureType }} × {{ item.count }}</strong>
                <span>{{ item.suggestion }}</span>
              </div>
            </div>
            <el-empty v-else description="最近批次暂无失败分类" />
          </div>
        </div>

        <el-alert
          v-for="item in healthReport.qualitySuggestions"
          :key="item"
          class="suggestion-alert"
          type="warning"
          :closable="false"
          :title="item"
        />
      </template>
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
        <div class="section-actions">
          <el-button type="info" :loading="loadingQuality" @click="openQualityDialog">检查用例质量</el-button>
          <el-button :loading="loadingCases" @click="loadCases">刷新</el-button>
        </div>
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
            <el-button link type="danger" @click="openDiagnosisDialog(row)">诊断</el-button>
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

    <el-dialog v-model="templateDialogVisible" title="生成核心链路模板" width="560px">
      <el-form :model="templateForm" label-width="120px">
        <el-form-item label="目标数据集">
          <el-select v-model="templateForm.datasetId" placeholder="请选择数据集" style="width: 100%">
            <el-option v-for="item in datasetOptions" :key="item.id" :label="item.datasetName" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="测试用户ID">
          <el-input-number v-model="templateForm.userId" :min="1" style="width: 100%" />
        </el-form-item>
        <el-form-item label="覆盖旧模板">
          <el-switch v-model="templateForm.overwrite" active-text="覆盖" inactive-text="跳过已有" />
        </el-form-item>
        <el-alert
          type="info"
          :closable="false"
          title="将生成 TOOL_CALL、RAG_RETRIEVAL、MEMORY_RECALL、GUARDRAIL、JSON_OUTPUT 五类基础用例。默认跳过已有模板，避免覆盖你手动调好的用例。"
        />
      </el-form>
      <template #footer>
        <el-button @click="templateDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="creatingTemplates" @click="submitCoreTemplates">生成</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="diagnosisDialogVisible" title="Eval 失败诊断" width="760px">
      <div v-loading="loadingDiagnosis" class="diagnosis-content">
        <template v-if="diagnosis">
          <el-alert
            type="warning"
            :closable="false"
            :title="diagnosis.summary || '暂无诊断摘要'"
          />
          <div class="diagnosis-meta">
            <el-tag :type="priorityTagType(diagnosis.priority)">优先级: {{ diagnosis.priority || "-" }}</el-tag>
            <el-tag type="info">失败分类: {{ diagnosis.failureType || "-" }}</el-tag>
            <el-tag :type="diagnosis.passStatus === 1 ? 'success' : 'danger'">
              {{ diagnosis.passStatus === 1 ? "已通过" : "未通过" }}
            </el-tag>
          </div>
          <div class="diagnosis-section">
            <h3>可能根因</h3>
            <ul>
              <li v-for="item in diagnosis.rootCauses" :key="item">{{ item }}</li>
            </ul>
          </div>
          <div class="diagnosis-section">
            <h3>建议操作</h3>
            <ul>
              <li v-for="item in diagnosis.suggestions" :key="item">{{ item }}</li>
            </ul>
          </div>
          <div class="diagnosis-section">
            <h3>快捷修复</h3>
            <div class="quick-fix-row">
              <el-button
                v-for="item in availableQuickFixes"
                :key="item.actionType"
                size="small"
                :type="item.buttonType"
                :loading="applyingQuickFix === item.actionType"
                @click="applyQuickFix(item)"
              >
                {{ item.label }}
              </el-button>
              <span v-if="!availableQuickFixes.length" class="muted-text">当前失败类型暂无安全快捷修复。</span>
            </div>
          </div>
          <div class="diagnosis-section">
            <h3>诊断依据</h3>
            <ul>
              <li v-for="item in diagnosis.evidence" :key="item">{{ item }}</li>
            </ul>
          </div>
        </template>
      </div>
    </el-dialog>

    <el-dialog v-model="qualityDialogVisible" title="Eval 用例质量检查" width="1080px">
      <div v-loading="loadingQuality" class="quality-content">
        <div v-if="qualityReport" class="quality-summary">
          <div>
            <span>检查用例</span>
            <strong>{{ qualityReport.totalCaseCount }}</strong>
          </div>
          <div>
            <span>问题用例</span>
            <strong>{{ qualityReport.problemCaseCount }}</strong>
          </div>
          <div>
            <span>高风险</span>
            <strong class="risk-high">{{ qualityReport.highRiskIssueCount }}</strong>
          </div>
          <div>
            <span>中风险</span>
            <strong class="risk-medium">{{ qualityReport.mediumRiskIssueCount }}</strong>
          </div>
          <div>
            <span>低风险</span>
            <strong>{{ qualityReport.lowRiskIssueCount }}</strong>
          </div>
        </div>

        <el-alert
          v-if="qualityReport && !qualityReport.issues.length"
          type="success"
          :closable="false"
          title="当前启用用例暂未发现明显配置问题，可以继续执行回归。"
        />

        <el-table v-if="qualityReport?.issues.length" :data="qualityReport.issues" border stripe max-height="520">
          <el-table-column prop="caseId" label="用例ID" width="90" />
          <el-table-column prop="caseName" label="用例名称" min-width="170" />
          <el-table-column prop="evalType" label="类型" width="140" />
          <el-table-column prop="riskLevel" label="风险" width="100">
            <template #default="{ row }">
              <el-tag :type="riskTagType(row.riskLevel)" effect="light">{{ row.riskLevel }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="issueType" label="问题编码" min-width="190" />
          <el-table-column prop="issueMessage" label="问题说明" min-width="260" show-overflow-tooltip />
          <el-table-column prop="suggestion" label="修复建议" min-width="260" show-overflow-tooltip />
          <el-table-column label="操作" fixed="right" width="180">
            <template #default="{ row }">
              <el-button
                v-if="row.fixable"
                link
                type="success"
                :loading="fixingQualityCaseId === row.caseId"
                @click="applyQualityFix(row)"
              >
                {{ row.fixButtonText || "快捷修复" }}
              </el-button>
              <el-button link type="primary" @click="editCaseFromQualityIssue(row)">编辑用例</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
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
  applyEvalCaseQualityFix,
  applyEvalQuickFix,
  checkEvalCaseQuality,
  createEvalCoreTemplates,
  deleteEvalCase,
  deleteEvalDataset,
  diagnoseEvalResult,
  getEvalHealthReport,
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
  AgentEvalCaseQualityIssue,
  AgentEvalCaseQualityReport,
  AgentEvalCaseQuery,
  AgentEvalCoreTemplateCreatePayload,
  AgentEvalDatasetInfo,
  AgentEvalDatasetQuery,
  AgentEvalHealthMetric,
  AgentEvalHealthReport,
  AgentEvalResultDiagnosis,
  AgentEvalResultInfo,
  AgentEvalResultQuery,
  AgentEvalRunInfo,
  AgentEvalRunQuery
} from "../../api/types";

const evalTypes = ["END_TO_END", "TOOL_CALL", "RAG_RETRIEVAL", "MEMORY_RECALL", "GUARDRAIL", "JSON_OUTPUT", "ANSWER_QUALITY"];

type QuickFixItem = {
  actionType: string;
  label: string;
  confirmText: string;
  buttonType: "primary" | "success" | "warning" | "danger" | "info";
};

const datasets = ref<AgentEvalDatasetInfo[]>([]);
const datasetOptions = ref<AgentEvalDatasetInfo[]>([]);
const cases = ref<AgentEvalCaseInfo[]>([]);
const runs = ref<AgentEvalRunInfo[]>([]);
const results = ref<AgentEvalResultInfo[]>([]);
const healthReport = ref<AgentEvalHealthReport>();
const qualityReport = ref<AgentEvalCaseQualityReport>();
const diagnosis = ref<AgentEvalResultDiagnosis>();
const diagnosisResultRow = ref<AgentEvalResultInfo>();
const caseTotal = ref(0);
const resultTotal = ref(0);

const loadingDatasets = ref(false);
const loadingCases = ref(false);
const loadingRuns = ref(false);
const loadingResults = ref(false);
const loadingHealth = ref(false);
const loadingQuality = ref(false);
const loadingDiagnosis = ref(false);
const running = ref(false);
const savingDataset = ref(false);
const savingCase = ref(false);
const creatingTemplates = ref(false);
const applyingQuickFix = ref("");
const fixingQualityCaseId = ref<number>();

const datasetDialogVisible = ref(false);
const caseDialogVisible = ref(false);
const templateDialogVisible = ref(false);
const qualityDialogVisible = ref(false);
const diagnosisDialogVisible = ref(false);
const jsonDialogVisible = ref(false);
const jsonDialogTitle = ref("");
const jsonDialogContent = ref("");

const datasetQuery = reactive<AgentEvalDatasetQuery>({ pageNum: 1, pageSize: 20 });
const caseQuery = reactive<AgentEvalCaseQuery>({ pageNum: 1, pageSize: 10 });
const runQuery = reactive<AgentEvalRunQuery>({ pageNum: 1, pageSize: 10 });
const resultQuery = reactive<AgentEvalResultQuery>({ pageNum: 1, pageSize: 10 });

const datasetForm = reactive<AgentEvalDatasetInfo>(emptyDatasetForm());
const caseForm = reactive<AgentEvalCaseInfo>(emptyCaseForm());
const templateForm = reactive<AgentEvalCoreTemplateCreatePayload>({
  datasetId: undefined,
  userId: 1,
  overwrite: false
});

const latestRun = computed(() => runs.value[0]);
const latestPassRate = computed(() => {
  const run = latestRun.value;
  if (!run || !run.totalCount) return 0;
  return (run.passCount * 100) / run.totalCount;
});
const latestFailureStats = computed(() => parseFailureStats(latestRun.value?.failureStatsJson));
const availableQuickFixes = computed(() => buildQuickFixes(diagnosis.value, diagnosisResultRow.value));
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

async function loadHealthReport() {
  loadingHealth.value = true;
  try {
    healthReport.value = await getEvalHealthReport(caseQuery.datasetId);
  } finally {
    loadingHealth.value = false;
  }
}

async function openQualityDialog() {
  qualityDialogVisible.value = true;
  loadingQuality.value = true;
  try {
    /*
     * 用例质量检查步骤:
     * 1. 复用当前用例筛选里的 datasetId，保证检查范围和页面正在关注的数据集一致。
     * 2. 后端只做规则检查，不调用模型，也不会修改用例，所以这里可以直接打开弹窗展示结果。
     * 3. 管理员看到问题后再点击“编辑用例”手动修复，避免自动改错 Eval 断言。
     */
    qualityReport.value = await checkEvalCaseQuality(caseQuery.datasetId);
  } finally {
    loadingQuality.value = false;
  }
}

async function reloadAll() {
  await Promise.all([loadDatasets(), loadCases(), loadRuns(), loadResults(), loadHealthReport()]);
}

function openDatasetDialog(row?: AgentEvalDatasetInfo) {
  Object.assign(datasetForm, emptyDatasetForm(), row || {});
  datasetDialogVisible.value = true;
}

function openCaseDialog(row?: AgentEvalCaseInfo) {
  Object.assign(caseForm, emptyCaseForm(), row || {});
  caseDialogVisible.value = true;
}

async function editCaseFromQualityIssue(issue: AgentEvalCaseQualityIssue) {
  /*
   * 从质量检查结果跳转编辑步骤:
   * 1. 先在当前分页数据里按 caseId 查找，命中时直接打开编辑弹窗，体验最快。
   * 2. 如果当前页没有这条用例，就按用例名称搜索并刷新列表，避免额外新增单条详情接口。
   * 3. 搜索后再次尝试打开，找不到时给出明确提示，让管理员知道需要手动定位。
   */
  const matchedCase = cases.value.find(item => item.id === issue.caseId);
  if (matchedCase) {
    openCaseDialog(matchedCase);
    return;
  }

  caseQuery.caseName = issue.caseName || "";
  caseQuery.pageNum = 1;
  await loadCases();
  const searchedCase = cases.value.find(item => item.id === issue.caseId);
  if (searchedCase) {
    openCaseDialog(searchedCase);
  } else {
    ElMessage.info("已按用例名称刷新列表，请在列表中继续查看或编辑。");
  }
}

async function applyQualityFix(issue: AgentEvalCaseQualityIssue) {
  /*
   * 质量快捷修复步骤:
   * 1. 只有后端标记 fixable 的问题才显示按钮，这里再次检查 caseId 和 actionType，避免空请求。
   * 2. 修复前弹出确认框，让管理员明确知道系统会写入默认断言值。
   * 3. 修复完成后同时刷新用例列表和质量报告，让问题是否消失可以立即看到。
   */
  if (!issue.caseId || !issue.fixActionType) {
    ElMessage.warning("该问题暂不支持快捷修复，请手动编辑用例。");
    return;
  }
  await ElMessageBox.confirm(
    issue.fixConfirmText || "确认应用该快捷修复吗？",
    "质量快捷修复确认",
    { type: "warning" }
  );
  fixingQualityCaseId.value = issue.caseId;
  try {
    await applyEvalCaseQualityFix(issue.caseId, { actionType: issue.fixActionType });
    ElMessage.success("质量快捷修复已应用");
    await Promise.all([loadCases(), openQualityDialog()]);
  } finally {
    fixingQualityCaseId.value = undefined;
  }
}

function openTemplateDialog() {
  Object.assign(templateForm, {
    datasetId: typeof caseQuery.datasetId === "number" ? caseQuery.datasetId : undefined,
    userId: 1,
    overwrite: false
  });
  templateDialogVisible.value = true;
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

async function submitCoreTemplates() {
  if (!templateForm.datasetId) {
    ElMessage.warning("请先选择目标数据集");
    return;
  }
  creatingTemplates.value = true;
  try {
    // 1. 后端会按数据集生成五类核心链路模板，默认跳过已有类型。
    // 2. 生成后刷新用例列表和体检报告，让覆盖率立即反映新模板。
    const result = await createEvalCoreTemplates(templateForm);
    ElMessage.success(`已生成 ${result.createdCount} 条模板，跳过 ${result.skippedCount} 条`);
    templateDialogVisible.value = false;
    caseQuery.datasetId = templateForm.datasetId;
    caseQuery.pageNum = 1;
    await Promise.all([loadCases(), loadHealthReport()]);
  } finally {
    creatingTemplates.value = false;
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
    await Promise.all([loadRuns(), loadResults(), loadHealthReport()]);
  } finally {
    running.value = false;
  }
}

async function runDataset(row: AgentEvalDatasetInfo) {
  running.value = true;
  try {
    await runEvalDataset(row.id!);
    ElMessage.success("数据集回归完成");
    await Promise.all([loadRuns(), loadResults(), loadHealthReport()]);
  } finally {
    running.value = false;
  }
}

async function runAll() {
  running.value = true;
  try {
    await runAllEvalCases();
    ElMessage.success("全量回归完成");
    await Promise.all([loadRuns(), loadResults(), loadHealthReport()]);
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
  loadHealthReport();
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
  loadHealthReport();
}

function formatHealthMetric(item: AgentEvalHealthMetric) {
  if (item.metricValue === undefined || item.metricValue === null) return "-";
  return item.percentMetric === false ? String(item.metricValue) : `${item.metricValue}%`;
}

function metricTagType(status?: string) {
  if (status === "GOOD") return "success";
  if (status === "WARN") return "warning";
  if (status === "RISK") return "danger";
  return "info";
}

async function openDiagnosisDialog(row: AgentEvalResultInfo) {
  diagnosisDialogVisible.value = true;
  diagnosisResultRow.value = row;
  diagnosis.value = undefined;
  loadingDiagnosis.value = true;
  try {
    diagnosis.value = await diagnoseEvalResult(row.id);
  } finally {
    loadingDiagnosis.value = false;
  }
}

function buildQuickFixes(current?: AgentEvalResultDiagnosis, row?: AgentEvalResultInfo): QuickFixItem[] {
  if (!current || current.passStatus === 1) return [];
  const failureType = current.failureType || "";
  const fixes: QuickFixItem[] = [];

  if (failureType.includes("TOOL_SELECT")) {
    if (row?.actualTools && row.actualTools !== "[]") {
      fixes.push({
        actionType: "COPY_ACTUAL_TOOL_TO_EXPECTED",
        label: "复制实际工具为期望工具",
        confirmText: "确认把该结果的第一个实际工具写入原用例的期望工具吗？",
        buttonType: "primary"
      });
    }
    fixes.push({
      actionType: "CLEAR_EXPECTED_TOOL",
      label: "清空期望工具",
      confirmText: "确认清空原用例的期望工具和期望参数吗？这会让该用例不再强制检查工具调用。",
      buttonType: "warning"
    });
  }

  if (failureType.includes("ANSWER_KEYWORD")) {
    fixes.push({
      actionType: "CLEAR_ANSWER_KEYWORDS",
      label: "清空答案关键词",
      confirmText: "确认清空原用例的答案关键词吗？这会避免关键词过严造成误判。",
      buttonType: "warning"
    });
  }

  if (failureType.includes("RAG")) {
    fixes.push({
      actionType: "CLEAR_RAG_KEYWORDS",
      label: "清空 RAG 期望",
      confirmText: "确认清空原用例的 RAG 关键词、文档ID和切片ID吗？这会让该用例不再强制检查 RAG 命中。",
      buttonType: "warning"
    });
  }

  return fixes;
}

async function applyQuickFix(item: QuickFixItem) {
  if (!diagnosis.value?.resultId) return;
  await ElMessageBox.confirm(item.confirmText, "快捷修复确认", { type: "warning" });
  applyingQuickFix.value = item.actionType;
  try {
    await applyEvalQuickFix(diagnosis.value.resultId, { actionType: item.actionType });
    ElMessage.success("快捷修复已应用，已更新原 Eval 用例");
    await Promise.all([loadCases(), loadResults(), loadHealthReport()]);
    if (diagnosisResultRow.value) {
      diagnosis.value = await diagnoseEvalResult(diagnosisResultRow.value.id);
    }
  } finally {
    applyingQuickFix.value = "";
  }
}

function priorityTagType(priority?: string) {
  if (priority === "HIGH") return "danger";
  if (priority === "MEDIUM") return "warning";
  return "info";
}

function riskTagType(riskLevel?: string) {
  if (riskLevel === "HIGH") return "danger";
  if (riskLevel === "MEDIUM") return "warning";
  return "info";
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
.section-actions,
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

.health-card {
  margin-bottom: 16px;
}

.health-summary {
  display: grid;
  grid-template-columns: 2fr 1fr 1fr 1.2fr;
  gap: 12px;
  margin-bottom: 14px;
}

.health-summary > div,
.health-panel {
  padding: 14px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #fff;
}

.health-summary span {
  display: block;
  color: #6b7280;
  font-size: 13px;
}

.health-summary strong {
  display: block;
  margin-top: 8px;
  font-size: 20px;
}

.health-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.health-panel h3 {
  margin: 0 0 12px;
  font-size: 16px;
}

.health-panel p {
  margin: 10px 0 6px;
  color: #6b7280;
}

.health-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 90px 82px;
  align-items: center;
  gap: 8px;
  padding: 8px 0;
  border-bottom: 1px solid #f3f4f6;
}

.tag-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.failure-item {
  display: grid;
  gap: 4px;
  padding: 8px 0;
  border-bottom: 1px solid #f3f4f6;
}

.failure-item span,
.muted-text {
  color: #6b7280;
  font-size: 13px;
}

.suggestion-alert {
  margin-top: 10px;
}

.diagnosis-content {
  min-height: 180px;
}

.diagnosis-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin: 12px 0;
}

.diagnosis-section {
  padding: 12px 0;
  border-top: 1px solid #eef2f7;
}

.diagnosis-section h3 {
  margin: 0 0 8px;
  font-size: 15px;
}

.diagnosis-section ul {
  margin: 0;
  padding-left: 18px;
  color: #374151;
  line-height: 1.8;
}

.quick-fix-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.quality-content {
  min-height: 180px;
}

.quality-summary {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 14px;
}

.quality-summary > div {
  padding: 14px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #fff;
}

.quality-summary span {
  display: block;
  color: #6b7280;
  font-size: 13px;
}

.quality-summary strong {
  display: block;
  margin-top: 8px;
  font-size: 22px;
}

.risk-high {
  color: #dc2626;
}

.risk-medium {
  color: #d97706;
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

  .health-summary,
  .health-grid,
  .quality-summary {
    grid-template-columns: 1fr;
  }
}
</style>
