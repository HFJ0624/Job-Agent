<template>
  <main class="admin-page">
    <section class="page-header">
      <div>
        <p class="eyebrow">Mock Interview</p>
        <h1>模拟面试记录</h1>
        <p>查看用户 AI 面试会话、题目回答、音频文件和火山 ASR 识别结果。</p>
      </div>
      <el-button :loading="loading" @click="loadSessions">刷新</el-button>
    </section>

    <section class="table-card">
      <el-form :model="query" label-width="90px" class="filter-form">
        <el-row :gutter="12">
          <el-col :span="5">
            <el-form-item label="用户ID">
              <el-input v-model.trim="query.userId" clearable placeholder="用户ID" />
            </el-form-item>
          </el-col>
          <el-col :span="5">
            <el-form-item label="岗位ID">
              <el-input v-model.trim="query.jobId" clearable placeholder="岗位ID" />
            </el-form-item>
          </el-col>
          <el-col :span="5">
            <el-form-item label="状态">
              <el-select v-model="query.status" clearable placeholder="全部">
                <el-option label="进行中" value="IN_PROGRESS" />
                <el-option label="已完成" value="FINISHED" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="关键词">
              <el-input v-model.trim="query.keyword" clearable placeholder="岗位/公司" />
            </el-form-item>
          </el-col>
          <el-col :span="3">
            <el-button type="primary" @click="search">查询</el-button>
          </el-col>
        </el-row>
      </el-form>

      <el-table v-loading="loading" :data="sessions" border stripe>
        <el-table-column prop="id" label="ID" width="90" />
        <el-table-column prop="jobTitle" label="岗位" min-width="180" />
        <el-table-column prop="companyName" label="公司" min-width="140" />
        <el-table-column prop="userId" label="用户ID" width="100" />
        <el-table-column prop="resumeId" label="简历ID" width="100" />
        <el-table-column prop="status" label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="row.status === 'FINISHED' ? 'success' : 'warning'">
              {{ row.status === "FINISHED" ? "已完成" : "进行中" }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="进度" width="110">
          <template #default="{ row }">{{ row.currentIndex }}/{{ row.totalQuestionCount }}</template>
        </el-table-column>
        <el-table-column prop="totalScore" label="总分" width="90" />
        <el-table-column prop="createTime" label="创建时间" width="170" />
        <el-table-column label="操作" fixed="right" width="120">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">详情</el-button>
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
          @size-change="loadSessions"
          @current-change="loadSessions"
        />
      </div>
    </section>

    <el-drawer v-model="detailVisible" title="面试详情" size="70%">
      <template v-if="detail">
        <el-descriptions :column="3" border>
          <el-descriptions-item label="会话ID">{{ detail.id }}</el-descriptions-item>
          <el-descriptions-item label="用户ID">{{ detail.userId }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ detail.status }}</el-descriptions-item>
          <el-descriptions-item label="岗位">{{ detail.jobTitle || "-" }}</el-descriptions-item>
          <el-descriptions-item label="公司">{{ detail.companyName || "-" }}</el-descriptions-item>
          <el-descriptions-item label="总分">{{ detail.totalScore ?? "-" }}</el-descriptions-item>
        </el-descriptions>

        <section class="detail-section">
          <div class="section-title-row">
            <h2>AI 复盘报告</h2>
            <div>
              <el-button :loading="reviewLoading" @click="loadReview">查看最新复盘</el-button>
              <el-button type="primary" :loading="reviewGenerating" @click="generateReview">生成/重新生成</el-button>
            </div>
          </div>

          <el-empty v-if="!review" description="暂无 AI 复盘，点击生成后可查看总体评分和逐题复盘" />

          <div v-else class="review-detail">
            <div class="review-summary">
              <div>
                <strong>{{ review.totalScore }} 分</strong>
                <span>{{ review.reviewLevel }}</span>
                <small>已回答 {{ review.answeredCount }} 题</small>
              </div>
              <el-tag>{{ review.source || "AI" }}</el-tag>
            </div>

            <el-descriptions :column="2" border>
              <el-descriptions-item label="优势总结">{{ review.strengthSummary || "-" }}</el-descriptions-item>
              <el-descriptions-item label="短板总结">{{ review.weaknessSummary || "-" }}</el-descriptions-item>
              <el-descriptions-item label="能力标签">
                <el-tag v-for="tag in review.abilityTags" :key="tag" size="small">{{ tag }}</el-tag>
              </el-descriptions-item>
              <el-descriptions-item label="需要补充">
                <el-tag v-for="item in review.weakQuestions" :key="item" size="small" type="warning">{{ item }}</el-tag>
              </el-descriptions-item>
              <el-descriptions-item label="提升计划" :span="2">
                <pre>{{ review.improvementPlan || "-" }}</pre>
              </el-descriptions-item>
            </el-descriptions>

            <el-table :data="review.questionReviews || []" border stripe class="question-review-table">
              <el-table-column prop="sortNo" label="序号" width="70" />
              <el-table-column prop="questionType" label="类型" width="100" />
              <el-table-column prop="questionContent" label="题目" min-width="220" />
              <el-table-column prop="standardAnswer" label="标准答案" min-width="240" show-overflow-tooltip />
              <el-table-column prop="userAnswer" label="用户回答" min-width="240" show-overflow-tooltip />
              <el-table-column prop="score" label="得分" width="80" />
              <el-table-column prop="similarityScore" label="相似度" width="90" />
              <el-table-column label="状态" width="150">
                <template #default="{ row }">
                  <el-tag :type="row.correct ? 'success' : 'danger'" size="small">
                    {{ row.correct ? "正确" : "待提升" }}
                  </el-tag>
                  <el-tag v-if="row.wrongBook" type="warning" size="small">错题</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="薄弱点/建议" min-width="260">
                <template #default="{ row }">
                  <div class="review-points">
                    <span v-for="item in row.missingPoints || []" :key="`missing-${item}`">缺失：{{ item }}</span>
                    <span v-for="item in row.knowledgePoints || []" :key="`knowledge-${item}`">知识点：{{ item }}</span>
                    <span v-for="item in row.suggestions || []" :key="`suggestion-${item}`">建议：{{ item }}</span>
                  </div>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </section>

        <section class="detail-section">
          <h2>题目与回答</h2>
          <el-table :data="questionRows" border stripe>
            <el-table-column prop="sortNo" label="序号" width="80" />
            <el-table-column prop="questionType" label="类型" width="110" />
            <el-table-column prop="questionContent" label="题目" min-width="260" />
            <el-table-column prop="answerContent" label="回答/ASR文本" min-width="260" />
            <el-table-column prop="score" label="得分" width="90" />
            <el-table-column prop="level" label="等级" width="100" />
          </el-table>
        </section>

        <section class="detail-section">
          <h2>媒体与 ASR</h2>
          <el-table :data="detail.mediaRecords || []" border stripe>
            <el-table-column prop="id" label="ID" width="80" />
            <el-table-column prop="questionId" label="题目ID" width="100" />
            <el-table-column prop="mediaType" label="类型" width="90" />
            <el-table-column prop="asrStatus" label="ASR状态" width="110">
              <template #default="{ row }">
                <el-tag :type="row.asrStatus === 'SUCCESS' ? 'success' : 'danger'">{{ row.asrStatus }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="asrText" label="识别文本" min-width="260" />
            <el-table-column prop="asrError" label="错误" min-width="200" />
            <el-table-column label="音频" width="220">
              <template #default="{ row }">
                <audio v-if="row.fileUrl" :src="row.fileUrl" controls />
                <span v-else>-</span>
              </template>
            </el-table-column>
            <el-table-column prop="createTime" label="创建时间" width="170" />
          </el-table>
        </section>
      </template>
    </el-drawer>
  </main>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { ElMessage } from "element-plus";
import { generateMockInterviewReviewAdmin, getMockInterviewReviewLatest, getMockInterviewSessionDetail, pageMockInterviewSessions } from "../../api/mockInterview";
import type { MockInterviewAnswerInfo, MockInterviewQuestionInfo, MockInterviewReviewInfo, MockInterviewSessionInfo, MockInterviewSessionQuery } from "../../api/types";

const loading = ref(false);
const sessions = ref<MockInterviewSessionInfo[]>([]);
const total = ref(0);
const detailVisible = ref(false);
const detail = ref<MockInterviewSessionInfo | null>(null);
const review = ref<MockInterviewReviewInfo | null>(null);
const reviewLoading = ref(false);
const reviewGenerating = ref(false);

const query = reactive<MockInterviewSessionQuery>({
  pageNum: 1,
  pageSize: 10
});

const questionRows = computed(() => {
  const answerMap = new Map<number, MockInterviewAnswerInfo>();
  (detail.value?.answers || []).forEach(item => answerMap.set(item.questionId, item));
  return (detail.value?.questions || []).map((question: MockInterviewQuestionInfo) => {
    const answer = answerMap.get(question.id);
    return {
      ...question,
      answerContent: answer?.answerContent || "-",
      score: answer?.score,
      level: answer?.level
    };
  });
});

async function loadSessions() {
  loading.value = true;
  try {
    const page = await pageMockInterviewSessions(query);
    sessions.value = page.records || [];
    total.value = Number(page.total || 0);
  } finally {
    loading.value = false;
  }
}

function search() {
  query.pageNum = 1;
  loadSessions();
}

async function openDetail(row: MockInterviewSessionInfo) {
  detail.value = await getMockInterviewSessionDetail(row.id);
  review.value = null;
  detailVisible.value = true;
  await loadReview();
}

async function loadReview() {
  if (!detail.value) return;
  reviewLoading.value = true;
  try {
    review.value = await getMockInterviewReviewLatest(detail.value.id);
  } catch {
    review.value = null;
  } finally {
    reviewLoading.value = false;
  }
}

async function generateReview() {
  if (!detail.value) return;
  reviewGenerating.value = true;
  try {
    review.value = await generateMockInterviewReviewAdmin(detail.value.id);
    ElMessage.success("AI 复盘已生成");
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : "AI 复盘生成失败");
  } finally {
    reviewGenerating.value = false;
  }
}

onMounted(loadSessions);
</script>

<style scoped>
.filter-form {
  margin-bottom: 12px;
}

.detail-section {
  margin-top: 20px;
}

.detail-section h2 {
  margin: 0 0 12px;
  font-size: 18px;
}

.section-title-row,
.review-summary {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.section-title-row h2 {
  margin: 0;
}

.review-detail {
  display: grid;
  gap: 14px;
}

.review-summary {
  padding: 14px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #f8fafc;
}

.review-summary strong {
  margin-right: 10px;
  color: #0f766e;
  font-size: 28px;
}

.review-summary span {
  margin-right: 10px;
  color: #0f172a;
  font-weight: 700;
}

.review-summary small {
  color: #64748b;
}

.review-detail :deep(.el-tag) {
  margin: 2px 4px 2px 0;
}

.review-detail pre {
  margin: 0;
  color: #475569;
  font-family: inherit;
  line-height: 1.7;
  white-space: pre-wrap;
}

.question-review-table {
  margin-top: 4px;
}

.review-points {
  display: grid;
  gap: 4px;
  color: #475569;
  font-size: 12px;
  line-height: 1.5;
}

audio {
  width: 190px;
}
</style>
