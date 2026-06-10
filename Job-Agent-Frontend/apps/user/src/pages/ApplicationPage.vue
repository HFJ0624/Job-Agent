<template>
  <main class="page-section application-page">
    <section class="application-hero">
      <div>
        <p class="eyebrow">求职进度</p>
        <h1>管理你的投递、沟通和面试状态</h1>
        <p>把感兴趣的岗位加入进度，持续跟踪每个岗位的沟通、投递和面试进展。</p>
      </div>
      <button class="primary-button large" type="button" :disabled="loading" @click="loadData">
        {{ loading ? "刷新中..." : "刷新进度" }}
      </button>
    </section>

    <section class="stats-grid">
      <article class="stats-card">
        <span>总记录</span>
        <strong>{{ stats?.totalCount || 0 }}</strong>
      </article>

      <article class="stats-card warning">
        <span>今日需跟进</span>
        <strong>{{ stats?.todayFollowCount || 0 }}</strong>
      </article>

      <article class="stats-card primary">
        <span>面试中</span>
        <strong>{{ stats?.interviewingCount || 0 }}</strong>
      </article>
    </section>

    <section class="status-board">
      <button
        v-for="status in statusOptions"
        :key="status.value"
        type="button"
        class="status-card"
        :class="{ active: query.status === status.value }"
        @click="filterByStatus(status.value)"
      >
        <span>{{ status.label }}</span>
        <strong>{{ stats?.statusCountMap?.[status.value] || 0 }}</strong>
      </button>

      <button
        type="button"
        class="status-card"
        :class="{ active: !query.status }"
        @click="filterByStatus('')"
      >
        <span>全部</span>
        <strong>{{ stats?.totalCount || 0 }}</strong>
      </button>
    </section>

    <section class="filter-card">
      <label>
        <span>关键词</span>
        <input v-model.trim="query.keyword" placeholder="搜索岗位、公司或备注" />
      </label>

      <label>
        <span>城市</span>
        <input v-model.trim="query.city" placeholder="例如 上海" />
      </label>

      <label>
        <span>优先级</span>
        <select v-model="query.priority">
          <option value="">全部</option>
          <option value="HIGH">高</option>
          <option value="NORMAL">普通</option>
          <option value="LOW">低</option>
        </select>
      </label>

      <button class="primary-button" type="button" @click="handleSearch">
        查询
      </button>
    </section>

    <p v-if="errorMessage" class="form-error">{{ errorMessage }}</p>

    <section class="application-list">
      <p v-if="loading" class="empty-state">正在加载求职记录...</p>

      <p v-else-if="!applications.length" class="empty-state">
        暂无求职记录，可以先在岗位详情页点击“加入求职进度”。
      </p>

      <article
        v-for="item in applications"
        :key="item.id"
        class="application-item"
      >
        <div class="application-main">
          <div class="title-row">
            <h3>{{ item.jobTitle }}</h3>
            <span class="status-pill">{{ item.statusText }}</span>
            <span class="priority-pill">{{ item.priorityText || "普通" }}</span>
          </div>

          <p class="meta-line">
            {{ item.companyName || "未知公司" }} · {{ item.city || "城市待补充" }} · {{ item.salaryText || "薪资面议" }}
          </p>

          <p class="time-line">
            投递：{{ item.applyTime || "未投递" }}
            ｜ 面试：{{ item.interviewTime || "暂无" }}
            ｜ 跟进：{{ item.nextFollowTime || "暂无" }}
          </p>

          <p class="note-line">
            备注：{{ item.note || "暂无备注" }}
          </p>

          <p class="action-line">
            最近动作：{{ item.lastAction || "暂无" }}
          </p>
        </div>

        <div class="application-actions">
          <select v-model="item.status" @change="changeStatus(item)">
            <option v-for="status in statusOptions" :key="status.value" :value="status.value">
              {{ status.label }}
            </option>
          </select>

           <div class="button-group">
            <button
              class="secondary-button"
              type="button"
              @click="openInterviewPrepare(item)"
            >
              面试准备
            </button>

            <button
              class="secondary-button"
              type="button"
              @click="openMockInterview(item)"
            >
              模拟面试
            </button>
          </div>

          <div class="button-group">
            <RouterLink class="secondary-button" :to="`/jobs/${item.jobId}`">
                        查看岗位
                      </RouterLink>

            <button class="danger-button" type="button" @click="removeApplication(item)">
              删除
            </button>
          </div>
          
        </div>
      </article>
    </section>

    <div v-if="total > query.pageSize" class="pagination-row">
      <button
        class="secondary-button"
        type="button"
        :disabled="query.pageNum <= 1"
        @click="changePage(query.pageNum - 1)"
      >
        上一页
      </button>

      <span>第 {{ query.pageNum }} 页 / 共 {{ totalPages }} 页</span>

      <button
        class="secondary-button"
        type="button"
        :disabled="query.pageNum >= totalPages"
        @click="changePage(query.pageNum + 1)"
      >
        下一页
      </button>
    </div>

    <el-drawer
      v-model="prepareDrawerVisible"
      direction="rtl"
      size="46%"
      :with-header="false"
    >
      <section class="prepare-drawer">
        <header class="prepare-header">
          <div>
            <p class="eyebrow">AI 面试准备</p>
            <h2>{{ currentApplication?.jobTitle || "面试准备" }}</h2>
            <span>{{ currentApplication?.companyName || "目标公司" }}</span>
          </div>

          <button class="text-button" type="button" @click="prepareDrawerVisible = false">
            关闭
          </button>
        </header>

        <button
          class="primary-button large"
          type="button"
          :disabled="prepareLoading"
          @click="handleGenerateInterviewPrepare"
        >
          {{ prepareLoading ? "生成中..." : currentPrepare ? "重新生成" : "生成面试准备" }}
        </button>

        <p v-if="prepareLoading" class="empty-state">正在生成面试准备...</p>

        <div v-if="currentPrepare" class="prepare-result">
          <section class="prepare-card">
            <h3>技术面试题</h3>
            <ol>
              <li v-for="item in currentPrepare.technicalQuestions" :key="item">{{ item }}</li>
            </ol>
          </section>

          <section class="prepare-card">
            <h3>项目追问题</h3>
            <ol>
              <li v-for="item in currentPrepare.projectQuestions" :key="item">{{ item }}</li>
            </ol>
          </section>

          <section class="prepare-card">
            <h3>HR 面试题</h3>
            <ol>
              <li v-for="item in currentPrepare.hrQuestions" :key="item">{{ item }}</li>
            </ol>
          </section>

          <section class="prepare-card suggestion">
            <h3>复习建议</h3>
            <ul>
              <li v-for="item in currentPrepare.reviewSuggestions" :key="item">{{ item }}</li>
            </ul>
          </section>

          <section class="prepare-card">
            <h3>总结</h3>
            <p>{{ currentPrepare.summary }}</p>
          </section>
        </div>
      </section>
    </el-drawer>

    <el-drawer
        v-model="mockDrawerVisible"
        direction="rtl"
        size="48%"
        :with-header="false"
      >
        <section class="mock-drawer">
          <header class="mock-header">
            <div>
              <p class="eyebrow">AI 模拟面试</p>
              <h2>{{ currentMockApplication?.jobTitle || "模拟面试" }}</h2>
              <span>{{ currentMockApplication?.companyName || "目标公司" }}</span>
            </div>

            <button class="text-button" type="button" @click="mockDrawerVisible = false">
              关闭
            </button>
          </header>

          <div v-if="!mockSession" class="mock-start-card">
            <p>本轮模拟面试会基于你的求职记录和面试准备内容生成问题，并对回答进行评分。</p>

            <button
              class="primary-button large"
              type="button"
              :disabled="mockLoading"
              @click="handleStartMockInterview"
            >
              {{ mockLoading ? "启动中..." : "开始模拟面试" }}
            </button>
          </div>

          <div v-else class="mock-session-card">
            <div class="mock-progress">
              <span>进度：{{ mockSession.currentIndex }} / {{ mockSession.totalQuestionCount }}</span>
              <span>状态：{{ mockSession.status === "FINISHED" ? "已完成" : "进行中" }}</span>
            </div>

            <section v-if="currentQuestion" class="mock-question-card">
              <p class="question-type">{{ currentQuestion.questionType }}</p>
              <h3>{{ currentQuestion.questionContent }}</h3>

              <textarea
                v-model.trim="mockAnswerText"
                placeholder="请输入你的回答，建议按照 背景-方案-职责-结果 的结构展开。"
              />

              <button
                class="primary-button large"
                type="button"
                :disabled="mockLoading"
                @click="handleSubmitMockAnswer"
              >
                {{ mockLoading ? "评分中..." : "提交回答并评分" }}
              </button>
            </section>

            <section v-if="latestAnswer" class="mock-feedback-card">
              <h3>本题评分：{{ latestAnswer.score }} 分 · {{ latestAnswer.level }}</h3>

              <h4>优点</h4>
              <ul>
                <li v-for="item in latestAnswer.strengths" :key="item">{{ item }}</li>
              </ul>

              <h4>问题</h4>
              <ul>
                <li v-for="item in latestAnswer.problems" :key="item">{{ item }}</li>
              </ul>

              <h4>建议</h4>
              <ul>
                <li v-for="item in latestAnswer.suggestions" :key="item">{{ item }}</li>
              </ul>
            </section>

            <section v-if="mockSession.status === 'FINISHED'" class="mock-summary-card">
              <h3>本轮总分：{{ mockSession.totalScore || 0 }} 分</h3>
              <p>{{ mockSession.summary }}</p>
            </section>

            <button
              v-if="mockSession.status !== 'FINISHED'"
              class="secondary-button"
              type="button"
              :disabled="mockLoading"
              @click="handleFinishMockInterview"
            >
              结束本轮模拟
            </button>
          </div>
        </section>
      </el-drawer>

      <el-drawer
        v-model="mockDrawerVisible"
        direction="rtl"
        size="48%"
        :with-header="false"
      >
        <section class="mock-drawer">
          <header class="mock-header">
            <div>
              <p class="eyebrow">AI 模拟面试</p>
              <h2>{{ currentMockApplication?.jobTitle || "模拟面试" }}</h2>
              <span>{{ currentMockApplication?.companyName || "目标公司" }}</span>
            </div>

            <button class="text-button" type="button" @click="mockDrawerVisible = false">
              关闭
            </button>
          </header>

          <div v-if="!mockSession" class="mock-start-card">
            <p>本轮模拟面试会基于你的求职记录和面试准备内容生成问题，并对回答进行评分。</p>

            <button
              class="primary-button large"
              type="button"
              :disabled="mockLoading"
              @click="handleStartMockInterview"
            >
              {{ mockLoading ? "启动中..." : "开始模拟面试" }}
            </button>
          </div>

          <div v-else class="mock-session-card">
            <div class="mock-progress">
              <span>进度：{{ mockSession.currentIndex }} / {{ mockSession.totalQuestionCount }}</span>
              <span>状态：{{ mockSession.status === "FINISHED" ? "已完成" : "进行中" }}</span>
            </div>

            <section v-if="currentQuestion" class="mock-question-card">
              <p class="question-type">{{ currentQuestion.questionType }}</p>
              <h3>{{ currentQuestion.questionContent }}</h3>

              <textarea
                v-model.trim="mockAnswerText"
                placeholder="请输入你的回答，建议按照 背景-方案-职责-结果 的结构展开。"
              />

              <button
                class="primary-button large"
                type="button"
                :disabled="mockLoading"
                @click="handleSubmitMockAnswer"
              >
                {{ mockLoading ? "评分中..." : "提交回答并评分" }}
              </button>
            </section>

            <section v-if="latestAnswer" class="mock-feedback-card">
              <h3>本题评分：{{ latestAnswer.score }} 分 · {{ latestAnswer.level }}</h3>

              <h4>优点</h4>
              <ul>
                <li v-for="item in latestAnswer.strengths" :key="item">{{ item }}</li>
              </ul>

              <h4>问题</h4>
              <ul>
                <li v-for="item in latestAnswer.problems" :key="item">{{ item }}</li>
              </ul>

              <h4>建议</h4>
              <ul>
                <li v-for="item in latestAnswer.suggestions" :key="item">{{ item }}</li>
              </ul>
            </section>

            <!-- 原来的本轮总结卡片 -->
            <section v-if="mockSession.status === 'FINISHED'" class="mock-summary-card">
              <h3>本轮总分：{{ mockSession.totalScore || 0 }} 分</h3>
              <p>{{ mockSession.summary }}</p>
            </section>

            <!-- 新增：模拟面试复盘报告，放在本轮总结后面 -->
            <section v-if="mockSession.status === 'FINISHED'" class="mock-review-card">
              <div class="mock-review-top">
                <div>
                  <h3>模拟面试复盘报告</h3>
                  <p>根据本轮回答评分，分析优势、短板和下一步提升计划。</p>
                </div>

                <button
                  class="primary-button"
                  type="button"
                  :disabled="reviewLoading"
                  @click="handleGenerateMockReview"
                >
                  {{ reviewLoading ? "生成中..." : mockReview ? "重新生成" : "生成复盘" }}
                </button>
              </div>

              <div v-if="mockReview" class="mock-review-result">
                <div class="review-score-card">
                  <strong>{{ mockReview.totalScore }} 分</strong>
                  <span>{{ mockReview.reviewLevel }}</span>
                  <small>已回答 {{ mockReview.answeredCount }} 题</small>
                </div>

                <section class="review-section">
                  <h4>优势总结</h4>
                  <p>{{ mockReview.strengthSummary }}</p>
                </section>

                <section class="review-section warning">
                  <h4>短板总结</h4>
                  <p>{{ mockReview.weaknessSummary }}</p>
                </section>

                <section class="review-section">
                  <h4>能力标签</h4>
                  <div class="tag-row">
                    <span v-for="tag in mockReview.abilityTags" :key="tag">
                      {{ tag }}
                    </span>
                  </div>
                </section>

                <section class="review-section warning">
                  <h4>薄弱题目</h4>
                  <ul>
                    <li v-for="item in mockReview.weakQuestions" :key="item">
                      {{ item }}
                    </li>
                  </ul>
                </section>

                <section class="review-section suggestion">
                  <h4>提升计划</h4>
                  <pre>{{ mockReview.improvementPlan }}</pre>
                </section>
              </div>
            </section>

            <!-- 结束按钮放在复盘报告后面 -->
            <button
              v-if="mockSession.status !== 'FINISHED'"
              class="secondary-button"
              type="button"
              :disabled="mockLoading"
              @click="handleFinishMockInterview"
            >
              结束本轮模拟
            </button>
          </div>
        </section>
      </el-drawer>

      <ReminderPanel />
      
  </main>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { RouterLink } from "vue-router";
import { ElMessage, ElMessageBox } from "element-plus";
import {
  deleteApplication,
  getApplicationStats,
  pageApplications,
  updateApplicationStatus
} from "../api/application";
import type {
  JobApplicationInfo,
  JobApplicationStatsInfo
} from "../api/types";
import {
  generateInterviewPrepare,
  getLatestInterviewPrepare
} from "../api/interviewPrepare";
import type { InterviewPrepareInfo } from "../api/types";
import {
  finishMockInterview,
  getCurrentMockQuestion,
  getMockInterviewDetail,
  startMockInterview,
  submitMockAnswer
} from "../api/mockInterview";
import type {
  MockInterviewAnswerInfo,
  MockInterviewQuestionInfo,
  MockInterviewSessionInfo
} from "../api/types";

import {
  generateMockInterviewReview,
  getLatestMockInterviewReview
} from "../api/mockInterviewReview";

import type { MockInterviewReviewInfo } from "../api/types";
import ReminderPanel from "../components/ReminderPanel.vue";

/**
 * 状态选项。
 */
const statusOptions = [
  { value: "INTERESTED", label: "感兴趣" },
  { value: "COMMUNICATED", label: "已沟通" },
  { value: "APPLIED", label: "已投递" },
  { value: "INTERVIEWING", label: "面试中" },
  { value: "OFFER", label: "Offer" },
  { value: "REJECTED", label: "已拒绝" },
  { value: "CLOSED", label: "已结束" }
];

/**
 * 查询参数。
 */
const query = reactive({
  pageNum: 1,
  pageSize: 10,
  status: "",
  keyword: "",
  city: "",
  priority: ""
});

const applications = ref<JobApplicationInfo[]>([]);
const stats = ref<JobApplicationStatsInfo | null>(null);
const total = ref(0);
const loading = ref(false);
const errorMessage = ref("");
const prepareDrawerVisible = ref(false);
const prepareLoading = ref(false);
const currentPrepare = ref<InterviewPrepareInfo | null>(null);
const currentApplication = ref<JobApplicationInfo | null>(null);
const mockDrawerVisible = ref(false);
const mockLoading = ref(false);
const mockAnswerText = ref("");
const currentMockApplication = ref<JobApplicationInfo | null>(null);
const mockSession = ref<MockInterviewSessionInfo | null>(null);
const currentQuestion = ref<MockInterviewQuestionInfo | null>(null);
const latestAnswer = ref<MockInterviewAnswerInfo | null>(null);

/**
 * 当前模拟面试复盘报告。
 */
const mockReview = ref<MockInterviewReviewInfo | null>(null);

/**
 * 复盘报告生成加载状态。
 */
const reviewLoading = ref(false);

const totalPages = computed(() => {
  return Math.max(1, Math.ceil(total.value / query.pageSize));
});

onMounted(loadData);

/**
 * 加载统计和列表。
 */
async function loadData() {
  loading.value = true;
  errorMessage.value = "";

  try {
    const [statsResult, pageResult] = await Promise.all([
      getApplicationStats(),
      pageApplications(query)
    ]);

    stats.value = statsResult;
    applications.value = pageResult.records || [];
    total.value = pageResult.total || 0;
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : "求职进度加载失败";
    ElMessage.error(errorMessage.value);
  } finally {
    loading.value = false;
  }
}

/**
 * 按状态筛选。
 */
function filterByStatus(status: string) {
  query.status = status;
  query.pageNum = 1;
  loadData();
}

/**
 * 搜索。
 */
function handleSearch() {
  query.pageNum = 1;
  loadData();
}

/**
 * 翻页。
 */
function changePage(page: number) {
  query.pageNum = page;
  loadData();
}

/**
 * 修改状态。
 */
async function changeStatus(item: JobApplicationInfo) {
  try {
    const updated = await updateApplicationStatus(item.id, {
      status: item.status
    });

    Object.assign(item, updated);
    ElMessage.success("状态已更新");

    stats.value = await getApplicationStats();
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : "状态更新失败");
    await loadData();
  }
}

/**
 * 删除记录。
 */
async function removeApplication(item: JobApplicationInfo) {
  try {
    await ElMessageBox.confirm(
      `确定删除「${item.jobTitle}」的求职记录吗？`,
      "删除求职记录",
      {
        type: "warning",
        confirmButtonText: "删除",
        cancelButtonText: "取消"
      }
    );

    await deleteApplication(item.id);
    ElMessage.success("求职记录已删除");
    await loadData();
  } catch (error) {
    if (error === "cancel" || error === "close") {
      return;
    }

    ElMessage.error(error instanceof Error ? error.message : "删除失败");
  }
}

/**
 * 打开面试准备抽屉。
 */
async function openInterviewPrepare(item: JobApplicationInfo) {
  currentApplication.value = item;
  currentPrepare.value = null;
  prepareDrawerVisible.value = true;
  prepareLoading.value = true;

  try {
    currentPrepare.value = await getLatestInterviewPrepare(item.id);
  } catch (error) {
    console.error("[Job-Agent] 查询面试准备失败", error);
  } finally {
    prepareLoading.value = false;
  }
}

/**
 * 生成面试准备。
 */
async function handleGenerateInterviewPrepare() {
  if (!currentApplication.value) {
    return;
  }

  prepareLoading.value = true;

  try {
    currentPrepare.value = await generateInterviewPrepare({
      applicationId: currentApplication.value.id,
      resumeId: currentApplication.value.resumeId
    });

    ElMessage.success("面试准备已生成");
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : "面试准备生成失败");
  } finally {
    prepareLoading.value = false;
  }
}

/**
 * 打开模拟面试抽屉。
 */
async function openMockInterview(item: JobApplicationInfo) {
  currentMockApplication.value = item;
  mockDrawerVisible.value = true;
  mockSession.value = null;
  currentQuestion.value = null;
  latestAnswer.value = null;
  mockAnswerText.value = "";
}

/**
 * 开始模拟面试。
 */
async function handleStartMockInterview() {
  if (!currentMockApplication.value) {
    return;
  }

  mockLoading.value = true;
  
  try {
    mockSession.value = await startMockInterview({
      applicationId: currentMockApplication.value.id,
      resumeId: currentMockApplication.value.resumeId,
      questionCount: 6
    });

    currentQuestion.value = await getCurrentMockQuestion(mockSession.value.id);
    ElMessage.success("模拟面试已开始");
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : "模拟面试启动失败");
  } finally {
    mockLoading.value = false;
  }
}

/**
 * 手动结束模拟面试。
 */
async function handleFinishMockInterview() {
  if (!mockSession.value) {
    return;
  }

  mockLoading.value = true;

  try {
    mockSession.value = await finishMockInterview(mockSession.value.id);
    currentQuestion.value = null;

    /**
     * 面试结束后，先尝试加载历史复盘。
     * 如果没有，用户可以点击按钮生成。
     */
    await loadLatestMockReview();

    ElMessage.success("模拟面试已结束");
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : "结束模拟面试失败");
  } finally {
    mockLoading.value = false;
  }
}

/**
 * 查询当前模拟面试最近一次复盘报告。
 */
async function loadLatestMockReview() {
  if (!mockSession.value) {
    return;
  }

  try {
    mockReview.value = await getLatestMockInterviewReview(mockSession.value.id);
  } catch (error) {
    /**
     * 没有复盘报告不是严重错误，不打断用户流程。
     */
    console.error("[Job-Agent] 查询模拟面试复盘失败", error);
  }
}

/**
 * 生成模拟面试复盘报告。
 */
async function handleGenerateMockReview() {
  if (!mockSession.value) {
    return;
  }

  reviewLoading.value = true;

  try {
    mockReview.value = await generateMockInterviewReview(mockSession.value.id);
    ElMessage.success("模拟面试复盘已生成");
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : "复盘报告生成失败");
  } finally {
    reviewLoading.value = false;
  }
}

/**
 * 提交当前问题回答。
 */
async function handleSubmitMockAnswer() {
  if (!mockSession.value || !currentQuestion.value) {
    return;
  }

  if (!mockAnswerText.value.trim()) {
    ElMessage.warning("请先输入回答内容");
    return;
  }

  mockLoading.value = true;

  try {
    latestAnswer.value = await submitMockAnswer(mockSession.value.id, {
      questionId: currentQuestion.value.id,
      answerContent: mockAnswerText.value
    });

    mockAnswerText.value = "";

    mockSession.value = await getMockInterviewDetail(mockSession.value.id);
    currentQuestion.value = await getCurrentMockQuestion(mockSession.value.id);

    if (!currentQuestion.value) {
      ElMessage.success("本轮模拟面试已完成");
    } else {
      ElMessage.success("回答已评分，进入下一题");
    }
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : "提交回答失败");
  } finally {
    mockLoading.value = false;
  }
}
</script>

<style scoped>
.application-page {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.application-hero,
.filter-card {
  padding: 24px;
  border-radius: 22px;
  background: #ffffff;
  border: 1px solid #e5e7eb;
}

.application-hero {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: linear-gradient(135deg, #eff6ff, #ffffff);
}

.application-hero h1 {
  margin: 6px 0;
  color: #111827;
}

.application-hero p {
  color: #6b7280;
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
}

.stats-card {
  padding: 18px;
  border-radius: 18px;
  background: #ffffff;
  border: 1px solid #e5e7eb;
}

.stats-card span {
  color: #6b7280;
}

.stats-card strong {
  display: block;
  margin-top: 8px;
  font-size: 28px;
  color: #111827;
}

.stats-card.warning {
  background: #fff7ed;
  border-color: #fed7aa;
}

.stats-card.primary {
  background: #eff6ff;
  border-color: #bfdbfe;
}

.status-board {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.status-card {
  padding: 14px;
  border-radius: 16px;
  border: 1px solid #e5e7eb;
  background: #ffffff;
  cursor: pointer;
  text-align: left;
}

.status-card.active {
  border-color: #2563eb;
  background: #eff6ff;
}

.status-card span {
  color: #6b7280;
}

.status-card strong {
  display: block;
  margin-top: 6px;
  color: #111827;
  font-size: 22px;
}

.filter-card {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr)) auto;
  align-items: end;
  gap: 14px;
}

.filter-card label {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.filter-card span {
  color: #374151;
  font-size: 13px;
  font-weight: 600;
}

.filter-card input,
.filter-card select,
.application-actions select {
  padding: 10px 12px;
  border-radius: 12px;
  border: 1px solid #d1d5db;
  font: inherit;
}

.application-list {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.application-item {
  display: flex;
  justify-content: space-between;
  gap: 18px;
  padding: 20px;
  border-radius: 18px;
  background: #ffffff;
  border: 1px solid #e5e7eb;
}

.application-main {
  flex: 1;
}

.title-row {
  display: flex;
  align-items: center;
  gap: 10px;
}

.title-row h3 {
  margin: 0;
  color: #111827;
}

.status-pill,
.priority-pill {
  padding: 4px 9px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 700;
}

.status-pill {
  background: #ecfdf5;
  color: #047857;
}

.priority-pill {
  background: #eff6ff;
  color: #2563eb;
}

.meta-line,
.time-line,
.note-line,
.action-line {
  margin: 8px 0;
  color: #6b7280;
}

.application-actions {
  display: flex;
  flex-direction: column;
  gap: 10px;
  min-width: 130px;
}

.pagination-row {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 14px;
}

@media (max-width: 900px) {
  .stats-grid,
  .status-board,
  .filter-card {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .application-item {
    flex-direction: column;
  }

  .application-actions {
    flex-direction: row;
    flex-wrap: wrap;
  }
}

@media (max-width: 640px) {
  .stats-grid,
  .status-board,
  .filter-card {
    grid-template-columns: 1fr;
  }

  .application-hero {
    flex-direction: column;
    align-items: flex-start;
  }
}
.prepare-drawer {
  min-height: 100%;
  padding: 24px;
  background: #f8fafc;
}

.prepare-header {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 18px;
}

.prepare-header h2 {
  margin: 4px 0;
  color: #111827;
}

.prepare-header span {
  color: #6b7280;
  font-size: 13px;
}

.prepare-result {
  display: flex;
  flex-direction: column;
  gap: 14px;
  margin-top: 16px;
}

.prepare-card {
  padding: 16px;
  border-radius: 16px;
  background: #ffffff;
  border: 1px solid #e5e7eb;
}

.prepare-card h3 {
  margin: 0 0 10px;
  color: #111827;
}

.prepare-card li {
  line-height: 1.8;
  color: #374151;
}

.prepare-card.suggestion {
  background: #f0fdf4;
  border-color: #bbf7d0;
}
.mock-drawer {
  min-height: 100%;
  padding: 24px;
  background: #f8fafc;
}

.mock-header {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 18px;
}

.mock-header h2 {
  margin: 4px 0;
  color: #111827;
}

.mock-header span {
  color: #6b7280;
  font-size: 13px;
}

.mock-start-card,
.mock-session-card,
.mock-question-card,
.mock-feedback-card,
.mock-summary-card {
  padding: 16px;
  border-radius: 16px;
  background: #ffffff;
  border: 1px solid #e5e7eb;
  margin-bottom: 14px;
}

.mock-progress {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  color: #374151;
  margin-bottom: 12px;
}

.question-type {
  color: #2563eb;
  font-weight: 700;
  margin: 0 0 8px;
}

.mock-question-card h3 {
  color: #111827;
  line-height: 1.6;
}

.mock-question-card textarea {
  width: 100%;
  min-height: 160px;
  resize: vertical;
  padding: 12px;
  margin: 12px 0;
  border-radius: 12px;
  border: 1px solid #d1d5db;
  font: inherit;
}

.mock-feedback-card h3,
.mock-summary-card h3 {
  color: #111827;
  margin-top: 0;
}

.mock-feedback-card h4 {
  margin: 12px 0 6px;
  color: #374151;
}

.mock-feedback-card li {
  line-height: 1.8;
  color: #374151;
}
.button-group {
  display: flex;
  /* 可选：设置按钮之间的间距 */
  gap: 8px;
  /* 可选：让按钮宽度平分 */
  /* justify-content: space-between; */
}
</style>