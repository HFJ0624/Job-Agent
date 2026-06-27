<template>
  <main class="admin-page question-bank-page">
    <section class="page-header question-bank-header">
      <div class="header-copy">
        <p class="eyebrow">Interview Question Bank</p>
        <h1>面试题库</h1>
        <p>从本地 Markdown 导入面试题和标准答案，并同步生成 RAG 文档与切片。</p>
      </div>
      <div class="header-actions">
        <el-button :loading="loading" @click="loadQuestions">刷新</el-button>
        <el-button type="warning" :loading="indexing" @click="handleIndexAll">重建全部索引</el-button>
      </div>
    </section>

    <section class="table-card import-panel">
      <div class="card-title-row">
        <div>
          <h2>本地题库导入</h2>
          <p>默认读取服务端目录，导入后可在 RAG 知识库用 documentType=INTERVIEW_QUESTION 查看。</p>
        </div>
        <el-tag type="info">Markdown</el-tag>
      </div>

      <el-form :model="importForm" label-position="top" class="import-form">
        <el-form-item label="本地目录" class="directory-item">
          <el-input v-model.trim="importForm.directoryPath" placeholder="D:\workspace\job-mcp-docs" />
        </el-form-item>
        <el-form-item label="同步 RAG" class="switch-item">
          <el-switch v-model="importForm.indexAfterImport" />
        </el-form-item>
        <el-form-item label="操作" class="action-item">
          <el-button type="primary" :loading="importing" @click="handleImport">导入本地题库</el-button>
        </el-form-item>
      </el-form>

      <el-alert
        v-if="importResult"
        class="import-result"
        type="success"
        :closable="false"
        show-icon
        :title="`扫描 ${importResult.scannedFileCount} 个文件，解析 ${importResult.parsedQuestionCount} 道题，新增 ${importResult.insertedCount}，更新 ${importResult.updatedCount}，索引 ${importResult.indexedCount}，失败 ${importResult.failedCount}`"
      />
    </section>

    <section class="table-card list-panel">
      <div class="card-title-row">
        <div>
          <h2>题目列表</h2>
          <p>按题目、答案、分类、难度和来源文件筛选题库内容。</p>
        </div>
      </div>

      <el-form :model="query" label-position="top" class="filter-form">
        <el-form-item label="关键词">
          <el-input v-model.trim="query.keyword" clearable placeholder="题目 / 答案 / 标签" />
        </el-form-item>
        <el-form-item label="分类">
          <el-input v-model.trim="query.category" clearable placeholder="Java / Spring" />
        </el-form-item>
        <el-form-item label="难度">
          <el-select v-model="query.difficulty" clearable placeholder="全部">
            <el-option label="EASY" value="EASY" />
            <el-option label="MEDIUM" value="MEDIUM" />
            <el-option label="HARD" value="HARD" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" clearable placeholder="全部">
            <el-option label="启用" value="ACTIVE" />
            <el-option label="禁用" value="DISABLED" />
          </el-select>
        </el-form-item>
        <el-form-item label="来源文件">
          <el-input v-model.trim="query.sourceFile" clearable placeholder="文件名" />
        </el-form-item>
        <el-form-item label="操作" class="filter-actions">
          <el-button type="primary" @click="search">查询</el-button>
        </el-form-item>
      </el-form>

      <el-table v-loading="loading" :data="questions" border stripe class="question-table">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="questionTitle" label="题目" min-width="280" show-overflow-tooltip />
        <el-table-column prop="category" label="分类" width="130" />
        <el-table-column prop="difficulty" label="难度" width="100" />
        <el-table-column prop="tags" label="标签" min-width="160" show-overflow-tooltip />
        <el-table-column prop="sourceFile" label="来源文件" min-width="180" show-overflow-tooltip />
        <el-table-column prop="ragDocumentId" label="RAG文档" width="100" />
        <el-table-column prop="ragChunkId" label="首个Chunk" width="110" />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'">
              {{ row.status === "ACTIVE" ? "启用" : "禁用" }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" fixed="right" width="220">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">详情</el-button>
            <el-button link type="warning" @click="handleIndex(row)">重建索引</el-button>
            <el-button link :type="row.status === 'ACTIVE' ? 'danger' : 'success'" @click="toggleStatus(row)">
              {{ row.status === "ACTIVE" ? "禁用" : "启用" }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-row">
        <el-pagination
          v-model:current-page="query.pageNum"
          v-model:page-size="query.pageSize"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          @size-change="loadQuestions"
          @current-change="loadQuestions"
        />
      </div>
    </section>

    <el-drawer v-model="detailVisible" title="面试题详情" size="60%">
      <template v-if="detail">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="题目ID">{{ detail.id }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ detail.status }}</el-descriptions-item>
          <el-descriptions-item label="分类">{{ detail.category || "-" }}</el-descriptions-item>
          <el-descriptions-item label="难度">{{ detail.difficulty || "-" }}</el-descriptions-item>
          <el-descriptions-item label="RAG文档ID">{{ detail.ragDocumentId || "-" }}</el-descriptions-item>
          <el-descriptions-item label="首个ChunkID">{{ detail.ragChunkId || "-" }}</el-descriptions-item>
          <el-descriptions-item label="来源文件">{{ detail.sourceFile || "-" }}</el-descriptions-item>
          <el-descriptions-item label="标签">{{ detail.tags || "-" }}</el-descriptions-item>
        </el-descriptions>

        <section class="detail-section">
          <h2>题目</h2>
          <p>{{ detail.questionTitle }}</p>
        </section>

        <section class="detail-section">
          <h2>标准答案</h2>
          <pre>{{ detail.standardAnswer }}</pre>
        </section>
      </template>
    </el-drawer>
  </main>
</template>

<script setup lang="ts">
import { ElMessage } from "element-plus";
import { onMounted, reactive, ref } from "vue";
import {
  getInterviewQuestionBankDetail,
  importLocalInterviewQuestions,
  indexAllInterviewQuestions,
  indexInterviewQuestion,
  pageInterviewQuestionBank,
  updateInterviewQuestionStatus
} from "../../api/interviewQuestionBank";
import type {
  InterviewQuestionBankInfo,
  InterviewQuestionBankQuery,
  InterviewQuestionImportPayload,
  InterviewQuestionImportResult
} from "../../api/types";

const loading = ref(false);
const importing = ref(false);
const indexing = ref(false);
const questions = ref<InterviewQuestionBankInfo[]>([]);
const total = ref(0);
const detailVisible = ref(false);
const detail = ref<InterviewQuestionBankInfo | null>(null);
const importResult = ref<InterviewQuestionImportResult | null>(null);

const importForm = reactive<InterviewQuestionImportPayload>({
  directoryPath: "D:\\workspace\\job-mcp-docs",
  indexAfterImport: true
});

const query = reactive<InterviewQuestionBankQuery>({
  pageNum: 1,
  pageSize: 10
});

async function loadQuestions() {
  loading.value = true;
  try {
    const page = await pageInterviewQuestionBank(query);
    questions.value = page.records || [];
    total.value = Number(page.total || 0);
  } finally {
    loading.value = false;
  }
}

function search() {
  query.pageNum = 1;
  loadQuestions();
}

async function handleImport() {
  importing.value = true;
  try {
    importResult.value = await importLocalInterviewQuestions(importForm);
    ElMessage.success("题库导入完成");
    await loadQuestions();
  } finally {
    importing.value = false;
  }
}

async function handleIndex(row: InterviewQuestionBankInfo) {
  await indexInterviewQuestion(row.id);
  ElMessage.success("索引重建完成");
  await loadQuestions();
}

async function handleIndexAll() {
  indexing.value = true;
  try {
    await indexAllInterviewQuestions();
    ElMessage.success("全部启用题目索引重建完成");
    await loadQuestions();
  } finally {
    indexing.value = false;
  }
}

async function toggleStatus(row: InterviewQuestionBankInfo) {
  const nextStatus = row.status === "ACTIVE" ? "DISABLED" : "ACTIVE";
  await updateInterviewQuestionStatus(row.id, nextStatus);
  ElMessage.success("状态已更新");
  await loadQuestions();
}

async function openDetail(row: InterviewQuestionBankInfo) {
  detail.value = await getInterviewQuestionBankDetail(row.id);
  detailVisible.value = true;
}

onMounted(loadQuestions);
</script>

<style scoped>
.question-bank-page {
  padding: 24px;
}

.question-bank-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 24px;
  margin-bottom: 18px;
}

.header-copy h1 {
  margin: 6px 0 8px;
  font-size: 28px;
  line-height: 1.2;
  color: #111827;
}

.header-copy p {
  margin: 0;
  color: #64748b;
}

.eyebrow {
  margin: 0;
  color: #2563eb;
  font-size: 13px;
  font-weight: 700;
}

.header-actions {
  display: flex;
  gap: 10px;
  flex-shrink: 0;
}

.table-card {
  padding: 18px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #fff;
}

.import-panel {
  margin-bottom: 16px;
}

.card-title-row {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}

.card-title-row h2 {
  margin: 0 0 6px;
  font-size: 18px;
  color: #111827;
}

.card-title-row p {
  margin: 0;
  color: #64748b;
  font-size: 13px;
}

.import-form {
  display: grid;
  grid-template-columns: minmax(360px, 1fr) 140px 150px;
  gap: 14px;
  align-items: end;
}

.filter-form {
  display: grid;
  grid-template-columns: minmax(220px, 1.2fr) minmax(150px, 0.8fr) minmax(130px, 0.7fr) minmax(130px, 0.7fr) minmax(180px, 1fr) 96px;
  gap: 14px;
  align-items: end;
  margin-bottom: 16px;
}

.import-form :deep(.el-form-item),
.filter-form :deep(.el-form-item) {
  margin-bottom: 0;
}

.import-form :deep(.el-select),
.filter-form :deep(.el-select) {
  width: 100%;
}

.action-item :deep(.el-button),
.filter-actions :deep(.el-button) {
  width: 100%;
}

.import-result {
  margin-top: 14px;
}

.question-table {
  width: 100%;
}

.pagination-row {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

.detail-section {
  margin-top: 20px;
}

.detail-section h2 {
  margin: 0 0 10px;
  font-size: 17px;
}

.detail-section p,
.detail-section pre {
  margin: 0;
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-word;
}

@media (max-width: 1200px) {
  .import-form,
  .filter-form {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 720px) {
  .question-bank-page {
    padding: 16px;
  }

  .question-bank-header,
  .card-title-row {
    flex-direction: column;
  }

  .header-actions {
    width: 100%;
  }

  .header-actions .el-button {
    flex: 1;
  }

  .import-form,
  .filter-form {
    grid-template-columns: 1fr;
  }
}
</style>
