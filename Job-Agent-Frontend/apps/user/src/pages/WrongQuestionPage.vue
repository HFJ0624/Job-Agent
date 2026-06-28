<template>
  <main class="page wrong-question-page">
    <section class="wrong-header">
      <div>
        <p class="eyebrow">Wrong Book</p>
        <h1>AI 面试错题本</h1>
        <p>集中复盘低分题、缺失要点和薄弱知识点，下一轮 AI 面试会优先围绕未掌握内容抽题。</p>
      </div>
      <el-button type="primary" :loading="loading" @click="loadWrongQuestions">刷新</el-button>
    </section>

    <section class="table-card filter-panel">
      <el-input
        v-model.trim="filters.keyword"
        clearable
        placeholder="搜索题目、知识点、缺失点"
        @keyup.enter="handleSearch"
      />
      <el-select v-model="filters.masteryStatus" clearable placeholder="掌握状态" @change="handleSearch">
        <el-option label="未掌握" value="UNMASTERED" />
        <el-option label="复习中" value="REVIEWING" />
        <el-option label="已掌握" value="MASTERED" />
      </el-select>
      <el-button type="primary" @click="handleSearch">查询</el-button>
    </section>

    <section class="wrong-list">
      <el-empty v-if="!loading && wrongQuestions.length === 0" description="暂无错题，完成一轮 AI 面试后会自动沉淀。" />

      <article v-for="item in wrongQuestions" :key="item.id" class="wrong-card">
        <div class="wrong-card-head">
          <div>
            <el-tag size="small">{{ item.questionType || "QUESTION" }}</el-tag>
            <el-tag :type="statusTagType(item.masteryStatus)" size="small">
              {{ statusText(item.masteryStatus) }}
            </el-tag>
          </div>
          <div class="score-box">
            <strong>{{ item.lastScore ?? "-" }}</strong>
            <span>最近得分</span>
          </div>
        </div>

        <h2>{{ item.questionContent }}</h2>
        <p class="wrong-reason">{{ item.wrongReason || "系统判定需要复练" }} · 错误 {{ item.wrongCount || 1 }} 次</p>

        <div class="answer-grid">
          <section>
            <h3>最近回答</h3>
            <p>{{ item.lastAnswerContent || "-" }}</p>
          </section>
          <section>
            <h3>标准答案</h3>
            <p>{{ item.standardAnswer || "-" }}</p>
          </section>
        </div>

        <div class="wrong-detail">
          <section>
            <h3>薄弱知识点</h3>
            <div class="tag-list">
              <el-tag v-for="point in item.knowledgePoints" :key="point" size="small">
                {{ point }}
              </el-tag>
            </div>
          </section>

          <section>
            <h3>缺失要点</h3>
            <ul>
              <li v-for="point in item.missingPoints" :key="point">{{ point }}</li>
            </ul>
          </section>

          <section>
            <h3>复习建议</h3>
            <ul>
              <li v-for="suggestion in item.suggestions" :key="suggestion">{{ suggestion }}</li>
            </ul>
          </section>
        </div>

        <div class="wrong-actions">
          <el-button
            size="small"
            :disabled="item.masteryStatus === 'UNMASTERED'"
            @click="changeStatus(item, 'UNMASTERED')"
          >
            标记未掌握
          </el-button>
          <el-button
            size="small"
            type="warning"
            :disabled="item.masteryStatus === 'REVIEWING'"
            @click="changeStatus(item, 'REVIEWING')"
          >
            标记复习中
          </el-button>
          <el-button
            size="small"
            type="success"
            :disabled="item.masteryStatus === 'MASTERED'"
            @click="changeStatus(item, 'MASTERED')"
          >
            标记已掌握
          </el-button>
        </div>
      </article>
    </section>

    <div class="pagination-row" v-if="total > filters.pageSize">
      <el-pagination
        layout="prev, pager, next"
        :page-size="filters.pageSize"
        :total="total"
        v-model:current-page="filters.pageNum"
        @current-change="loadWrongQuestions"
      />
    </div>
  </main>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import { ElMessage } from "element-plus";
import {
  pageMockInterviewWrongQuestions,
  updateMockInterviewWrongQuestionStatus
} from "../api/mockInterviewWrongQuestion";
import type { MockInterviewWrongQuestionInfo } from "../api/types";

const loading = ref(false);
const wrongQuestions = ref<MockInterviewWrongQuestionInfo[]>([]);
const total = ref(0);

const filters = reactive({
  pageNum: 1,
  pageSize: 10,
  keyword: "",
  masteryStatus: ""
});

onMounted(loadWrongQuestions);

async function loadWrongQuestions() {
  loading.value = true;
  try {
    const page = await pageMockInterviewWrongQuestions(filters);
    wrongQuestions.value = page.records || [];
    total.value = page.total || 0;
  } finally {
    loading.value = false;
  }
}

function handleSearch() {
  filters.pageNum = 1;
  loadWrongQuestions();
}

async function changeStatus(item: MockInterviewWrongQuestionInfo, masteryStatus: string) {
  const updated = await updateMockInterviewWrongQuestionStatus(item.id, masteryStatus);
  item.masteryStatus = updated.masteryStatus;
  ElMessage.success("掌握状态已更新");
}

function statusText(status: string) {
  if (status === "MASTERED") return "已掌握";
  if (status === "REVIEWING") return "复习中";
  return "未掌握";
}

function statusTagType(status: string) {
  if (status === "MASTERED") return "success";
  if (status === "REVIEWING") return "warning";
  return "danger";
}
</script>

<style scoped>
.wrong-question-page {
  max-width: 1180px;
  margin: 0 auto;
  padding: 32px 20px 40px;
}

.wrong-header,
.filter-panel,
.wrong-card-head,
.wrong-actions,
.pagination-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
}

.wrong-header {
  margin-bottom: 18px;
}

.wrong-header h1 {
  margin: 4px 0 8px;
  color: #0f172a;
}

.wrong-header p {
  margin: 0;
  color: #64748b;
}

.filter-panel {
  margin-bottom: 16px;
}

.filter-panel .el-input {
  max-width: 420px;
}

.wrong-list {
  display: grid;
  gap: 14px;
}

.wrong-card {
  padding: 16px;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  background: #ffffff;
}

.wrong-card h2 {
  margin: 12px 0 8px;
  color: #0f172a;
  font-size: 18px;
}

.wrong-reason {
  margin: 0 0 12px;
  color: #b45309;
}

.score-box {
  text-align: right;
}

.score-box strong {
  display: block;
  color: #0f766e;
  font-size: 24px;
}

.score-box span {
  color: #64748b;
  font-size: 12px;
}

.answer-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}

.answer-grid section,
.wrong-detail section {
  padding: 12px;
  border-radius: 8px;
  background: #f8fafc;
}

.answer-grid h3,
.wrong-detail h3 {
  margin: 0 0 8px;
  color: #334155;
  font-size: 14px;
}

.answer-grid p,
.wrong-detail li {
  color: #475569;
  line-height: 1.7;
}

.wrong-detail {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
  margin-top: 12px;
}

.tag-list {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.wrong-actions {
  justify-content: flex-end;
  margin-top: 14px;
}

.pagination-row {
  justify-content: center;
  margin-top: 18px;
}

@media (max-width: 900px) {
  .wrong-header,
  .filter-panel {
    align-items: stretch;
    flex-direction: column;
  }

  .filter-panel .el-input {
    max-width: none;
  }

  .answer-grid,
  .wrong-detail {
    grid-template-columns: 1fr;
  }
}
</style>
