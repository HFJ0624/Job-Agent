<template>
  <main class="page ai-interview-page">
    <section class="page-header ai-interview-header">
      <div>
        <p class="eyebrow">AI Interview</p>
        <h1>AI 模拟面试</h1>
        <p>选择简历和岗位后开始面试，系统会打开摄像头预览，并用火山引擎 ASR 识别你的语音回答。</p>
      </div>
      <el-tag v-if="session" :type="session.status === 'FINISHED' ? 'success' : 'warning'">
        {{ session.status === "FINISHED" ? "已完成" : "进行中" }}
      </el-tag>
    </section>

    <el-alert
      v-if="interviewError && !session"
      class="interview-error-alert global-error-alert"
      type="error"
      show-icon
      :closable="true"
      @close="clearInterviewError"
    >
      <template #title>
        <span>{{ interviewError.title }}</span>
      </template>
      <div class="interview-error-body">
        <p>{{ interviewError.message }}</p>
        <p>{{ interviewError.suggestion }}</p>
      </div>
    </el-alert>

    <section class="start-layout" v-if="!session">
      <div class="table-card ai-start-panel">
        <div class="card-title">
          <h2>开始一场模拟面试</h2>
          <p>选择一份简历和目标岗位，系统会按岗位要求生成面试流程。</p>
        </div>

        <el-form label-position="top" class="start-form">
          <el-form-item label="选择简历">
            <el-select v-model="form.resumeId" placeholder="请选择简历">
              <el-option v-for="item in resumes" :key="item.id" :label="item.resumeName" :value="item.id" />
            </el-select>
          </el-form-item>

          <el-form-item label="搜索岗位">
            <div class="job-search-row">
              <el-input v-model.trim="jobKeyword" placeholder="输入岗位名、城市、技能关键词" @keyup.enter="loadJobs" />
              <el-button :loading="loadingJobs" @click="loadJobs">搜索</el-button>
            </div>
          </el-form-item>

          <el-form-item label="选择岗位">
            <el-select v-model="form.jobId" filterable placeholder="请选择岗位">
              <el-option
                v-for="item in jobs"
                :key="item.id"
                :label="`${item.jobTitle} | ${item.companyName || '未知公司'} | ${item.city || '-'}`"
                :value="item.id"
              />
            </el-select>
          </el-form-item>

          <el-form-item label="题目数量">
            <el-input-number v-model="form.questionCount" :min="3" :max="12" />
          </el-form-item>

          <el-form-item label="最近抽题去重窗口">
            <div class="dedupe-setting">
              <el-input-number v-model="form.excludeRecentHours" :min="0" :max="720" :step="24" />
              <span class="muted">小时，填 0 表示允许重复抽题</span>
            </div>
          </el-form-item>

          <el-button class="start-button" type="primary" size="large" :loading="starting" @click="startInterview">
            开始面试
          </el-button>
        </el-form>
      </div>

      <aside class="interview-guide">
        <div class="guide-card primary-guide">
          <p class="eyebrow">Before Start</p>
          <h2>开始前检查</h2>
          <ul>
            <li>确认浏览器允许摄像头和麦克风权限。</li>
            <li>建议在安静环境中回答，语音识别会更稳定。</li>
            <li>每道题录音提交后，会生成 ASR 文本和得分建议。</li>
          </ul>
        </div>

        <div class="guide-grid">
          <div class="guide-card">
            <strong>1</strong>
            <span>选择简历</span>
            <p>用你的简历内容作为面试背景。</p>
          </div>
          <div class="guide-card">
            <strong>2</strong>
            <span>匹配岗位</span>
            <p>题目会围绕岗位技能和职责展开。</p>
          </div>
          <div class="guide-card">
            <strong>3</strong>
            <span>语音回答</span>
            <p>系统识别你的回答并保存记录。</p>
          </div>
          <div class="guide-card">
            <strong>4</strong>
            <span>查看建议</span>
            <p>面试结束后查看分数和优化方向。</p>
          </div>
        </div>
      </aside>
    </section>

    <section class="interview-layout" v-else>
      <div class="camera-panel">
        <div class="interview-status-card">
          <div class="status-head">
            <div>
              <span>当前进度</span>
              <strong>{{ answeredCount }} / {{ session.totalQuestionCount }}</strong>
            </div>
            <el-tag :type="currentQuestion ? 'warning' : 'success'">
              {{ interviewStageText }}
            </el-tag>
          </div>
          <el-progress :percentage="progressPercent" :stroke-width="10" />
          <div class="status-steps">
            <span :class="{ done: answeredCount > 0 }">已提交 {{ answeredCount }} 题</span>
            <span :class="{ active: recording }">录音中</span>
            <span :class="{ active: submitting }">识别与评分中</span>
            <span :class="{ done: !currentQuestion }">复盘阶段</span>
          </div>
        </div>
        <video ref="videoRef" autoplay playsinline muted></video>
        <div class="camera-actions">
          <el-button @click="openCamera">打开摄像头和麦克风</el-button>
          <el-button @click="closeCamera">关闭设备</el-button>
        </div>
        <p class="muted">第一版只做摄像头预览，不保存整段视频；每道题只上传回答音频。</p>
      </div>

      <div class="question-panel">
        <el-alert
          v-if="interviewError"
          class="interview-error-alert"
          type="error"
          show-icon
          :closable="true"
          @close="clearInterviewError"
        >
          <template #title>
            <span>{{ interviewError.title }}</span>
          </template>
          <div class="interview-error-body">
            <p>{{ interviewError.message }}</p>
            <p>{{ interviewError.suggestion }}</p>
            <div class="interview-error-actions">
              <el-button
                v-if="interviewError.action === 'OPEN_CAMERA'"
                size="small"
                type="primary"
                @click="openCamera"
              >
                重新打开设备
              </el-button>
              <el-button
                v-if="interviewError.action === 'RECORD_AGAIN'"
                size="small"
                type="primary"
                @click="startRecording"
              >
                重新录音
              </el-button>
              <el-button
                v-if="interviewError.action === 'RETRY_SUBMIT'"
                size="small"
                type="primary"
                :loading="submitting"
                @click="submitCurrentAudio"
              >
                重试提交
              </el-button>
              <el-button
                v-if="interviewError.action === 'RETRY_REVIEW'"
                size="small"
                type="primary"
                :loading="reviewLoading"
                @click="generateReview"
              >
                重新生成复盘
              </el-button>
              <el-button
                v-if="interviewError.action === 'RETRY_STUDY_PLAN'"
                size="small"
                type="primary"
                :loading="learningPlanGenerating"
                @click="generateLearningPlanFromReview"
              >
                重新生成学习计划
              </el-button>
              <el-button
                v-if="interviewError.action === 'RETRY_STUDY_MATERIAL'"
                size="small"
                type="primary"
                :loading="studyPlanLoading"
                @click="loadStudyPlan"
              >
                重新加载补课清单
              </el-button>
              <el-button size="small" @click="clearInterviewError">我知道了</el-button>
            </div>
          </div>
        </el-alert>

        <div v-if="currentQuestion">
          <p class="eyebrow">第 {{ session.currentIndex + 1 }} / {{ session.totalQuestionCount }} 题</p>
          <h2>{{ currentQuestion.questionContent }}</h2>
          <el-tag>{{ currentQuestion.questionType }}</el-tag>

          <div class="record-actions">
            <el-button v-if="!recording" type="danger" :disabled="submitting" @click="startRecording">开始录音</el-button>
            <el-button v-else type="warning" @click="stopRecording">停止并提交</el-button>
          </div>
          <div class="answer-flow">
            <span :class="{ active: recording, done: submitting }">1. 语音回答</span>
            <span :class="{ active: submitting }">2. ASR 识别</span>
            <span :class="{ active: submitting }">3. AI 单题评分</span>
            <span>4. 自动进入下一题</span>
          </div>
          <p class="muted" v-if="recording">录音中 {{ recordingSeconds }} 秒，请自然回答。</p>
          <p class="muted" v-if="submitting">正在上传音频并等待火山 ASR 识别...</p>
        </div>

        <el-result v-else icon="success" title="本轮面试已完成" :sub-title="session.summary || '可以在下方查看每道题得分和建议。'">
          <template #extra>
            <el-button type="success" :loading="reviewLoading" @click="generateReview">
              {{ review ? "重新生成 AI 总结" : "生成 AI 面试总结" }}
            </el-button>
            <el-button type="primary" @click="resetInterview">再来一轮</el-button>
          </template>
        </el-result>
      </div>
    </section>

    <section class="table-card review-panel" v-if="session && !currentQuestion">
      <div class="section-title-row">
        <div>
          <h2>AI 面试总结</h2>
          <p class="muted">基于本轮题目、标准答案、你的语音回答和单题评分生成。</p>
        </div>
        <el-button type="primary" :loading="reviewLoading" @click="generateReview">
          {{ review ? "重新生成" : "生成总结" }}
        </el-button>
      </div>

      <el-empty v-if="!review" description="面试结束后点击生成，查看总体评分、短板和补充建议。" />

      <div v-else class="review-content">
        <div class="review-score">
          <strong>{{ review.totalScore }} 分</strong>
          <span>{{ review.reviewLevel }}</span>
          <small>已回答 {{ review.answeredCount }} 题</small>
        </div>

        <div class="review-action-strip">
          <section>
            <strong>{{ weakQuestionCount }}</strong>
            <span>道题需要重点复盘</span>
          </section>
          <section>
            <strong>{{ review.weakQuestions?.length || 0 }}</strong>
            <span>个薄弱知识点</span>
          </section>
          <div class="review-next-actions">
            <el-button type="warning" @click="goWrongQuestions">查看错题本</el-button>
            <el-button type="success" :loading="learningPlanGenerating" @click="generateLearningPlanFromReview">
              生成学习计划
            </el-button>
            <el-button @click="goLearningPlan">进入学习计划</el-button>
          </div>
        </div>

        <div class="review-grid">
          <section class="review-block">
            <h3>优势总结</h3>
            <p>{{ review.strengthSummary || "-" }}</p>
          </section>

          <section class="review-block warning">
            <h3>短板总结</h3>
            <p>{{ review.weaknessSummary || "-" }}</p>
          </section>

          <section class="review-block">
            <h3>能力标签</h3>
            <div class="review-tags">
              <el-tag v-for="tag in review.abilityTags" :key="tag" type="success">
                {{ tag }}
              </el-tag>
            </div>
          </section>

          <section class="review-block warning">
            <h3>需要补充</h3>
            <ul>
              <li v-for="item in review.weakQuestions" :key="item">{{ item }}</li>
            </ul>
          </section>
        </div>

        <section class="review-block plan">
          <h3>AI 提升计划</h3>
          <pre>{{ review.improvementPlan }}</pre>
        </section>

        <section class="review-block question-review-section">
          <h3>逐题复盘详情</h3>
          <el-empty v-if="!review.questionReviews?.length" description="暂无逐题复盘明细" />
          <div v-else class="question-review-list">
            <article v-for="item in review.questionReviews" :key="item.questionId" class="question-review-card">
              <div class="question-review-head">
                <div>
                  <small>第 {{ item.sortNo || "-" }} 题 · {{ item.questionType || "未分类" }}</small>
                  <h4>{{ item.questionContent || "-" }}</h4>
                </div>
                <div class="question-review-tags">
                  <el-tag type="primary" size="small">{{ item.score ?? "-" }} 分</el-tag>
                  <el-tag size="small">{{ item.level || "未评分" }}</el-tag>
                  <el-tag :type="item.correct ? 'success' : 'danger'" size="small">
                    {{ item.correct ? "基本正确" : "需要复习" }}
                  </el-tag>
                  <el-tag v-if="item.wrongBook" type="warning" size="small">已入错题本</el-tag>
                </div>
              </div>

              <div class="question-answer-grid">
                <section>
                  <strong>标准答案</strong>
                  <p>{{ item.standardAnswer || "暂无标准答案" }}</p>
                </section>
                <section>
                  <strong>你的回答</strong>
                  <p>{{ item.userAnswer || "暂无回答" }}</p>
                </section>
              </div>

              <p class="question-review-conclusion">
                复盘结论：{{ item.reviewConclusion || "暂无单题复盘结论" }}
              </p>
              <p v-if="item.similarityScore !== undefined" class="muted">
                与标准答案相似度：{{ item.similarityScore }}%
              </p>

              <div class="point-grid">
                <section>
                  <strong>命中要点</strong>
                  <el-tag v-for="point in item.matchedPoints || []" :key="point" size="small" type="success">
                    {{ point }}
                  </el-tag>
                  <span v-if="!item.matchedPoints?.length" class="muted">暂无</span>
                </section>
                <section>
                  <strong>缺失要点</strong>
                  <el-tag v-for="point in item.missingPoints || []" :key="point" size="small" type="danger">
                    {{ point }}
                  </el-tag>
                  <span v-if="!item.missingPoints?.length" class="muted">暂无</span>
                </section>
                <section>
                  <strong>薄弱知识点</strong>
                  <el-tag v-for="point in item.knowledgePoints || []" :key="point" size="small" type="warning">
                    {{ point }}
                  </el-tag>
                  <span v-if="!item.knowledgePoints?.length" class="muted">暂无</span>
                </section>
                <section>
                  <strong>优化建议</strong>
                  <ul v-if="item.suggestions?.length">
                    <li v-for="suggestion in item.suggestions" :key="suggestion">{{ suggestion }}</li>
                  </ul>
                  <span v-else class="muted">暂无</span>
                </section>
              </div>
            </article>
          </div>
        </section>

        <section class="review-block study-plan">
          <div class="study-plan-title">
            <div>
              <h3>薄弱知识点补课清单</h3>
              <p>根据 AI 总结里的薄弱点，从 RAG 知识库召回学习材料。</p>
            </div>
            <div class="study-plan-actions">
              <el-button size="small" :loading="studyPlanLoading" @click="loadStudyPlan">刷新清单</el-button>
              <el-button size="small" type="primary" @click="goLearningPlan">完整学习计划</el-button>
            </div>
          </div>

          <el-empty v-if="!studyPlan?.items?.length" description="暂无补课材料，请确认 RAG 知识库已有相关内容。" />

          <div v-else class="study-list">
            <article v-for="item in studyPlan.items" :key="item.knowledgePoint" class="study-item">
              <h4>{{ item.knowledgePoint }}</h4>
              <p>{{ item.suggestion }}</p>
              <div class="study-materials">
                <section v-for="material in item.materials" :key="`${material.documentId}-${material.chunkId}`" class="study-material">
                  <strong>{{ material.title || "学习材料" }}</strong>
                  <p>{{ material.content }}</p>
                  <small v-if="material.score !== undefined">相关度 {{ material.score }}</small>
                </section>
              </div>
            </article>
          </div>
        </section>
      </div>
    </section>

    <section class="table-card answer-panel" v-if="session">
      <div class="section-title-row">
        <h2>回答记录</h2>
        <el-button :loading="loadingDetail" @click="reloadSession">刷新</el-button>
      </div>
      <el-table :data="answersWithQuestion" border stripe>
        <el-table-column prop="questionContent" label="题目" min-width="260" />
        <el-table-column prop="answerContent" label="ASR文本/回答" min-width="260" />
        <el-table-column prop="score" label="得分" width="90" />
        <el-table-column prop="level" label="等级" width="100" />
        <el-table-column label="建议" min-width="220">
          <template #default="{ row }">
            <div v-for="item in row.suggestions" :key="item">{{ item }}</div>
          </template>
        </el-table-column>
      </el-table>
      <div class="answer-review-list">
        <article v-for="row in answersWithQuestion" :key="`review-${row.id}`" class="answer-review-card">
          <div class="answer-review-head">
            <strong>{{ row.questionContent }}</strong>
            <div>
              <el-tag :type="row.correct ? 'success' : 'danger'" size="small">
                {{ row.correct ? "基本正确" : "需要复练" }}
              </el-tag>
              <el-tag v-if="row.wrongBook" type="warning" size="small">错题本</el-tag>
            </div>
          </div>
          <p>{{ row.reviewConclusion || "暂无单题复盘结论" }}</p>
          <div class="answer-tags">
            <el-tag v-for="item in row.knowledgePoints" :key="item" size="small">
              {{ item }}
            </el-tag>
          </div>
          <div v-if="row.missingPoints?.length" class="missing-list">
            <span v-for="item in row.missingPoints" :key="item">缺失：{{ item }}</span>
          </div>
        </article>
      </div>
    </section>
  </main>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref, watch } from "vue";
import { ElMessage } from "element-plus";
import { useRoute, useRouter } from "vue-router";
import { pageFrontPositions } from "../api/job";
import { listResumes } from "../api/resume";
import { ApiRequestError } from "../api/request";
import { getCurrentMockQuestion, getMockInterviewDetail, startAiInterview, submitMockAudioAnswer } from "../api/mockInterview";
import { generateMockInterviewReview, getLatestMockInterviewReview, getMockInterviewStudyPlan } from "../api/mockInterviewReview";
import { generateMockInterviewLearningPlan } from "../api/mockInterviewLearningPlan";
import type { MockInterviewAnswerInfo, MockInterviewQuestionInfo, MockInterviewReviewInfo, MockInterviewSessionInfo, MockInterviewStudyPlanInfo, PositionInfo, ResumeInfo } from "../api/types";

type InterviewErrorAction =
  | "OPEN_CAMERA"
  | "RECORD_AGAIN"
  | "RETRY_SUBMIT"
  | "RETRY_REVIEW"
  | "RETRY_STUDY_MATERIAL"
  | "RETRY_STUDY_PLAN";

interface InterviewErrorState {
  step: string;
  title: string;
  message: string;
  suggestion: string;
  action?: InterviewErrorAction;
}

const route = useRoute();
const router = useRouter();
const resumes = ref<ResumeInfo[]>([]);
const jobs = ref<PositionInfo[]>([]);
const session = ref<MockInterviewSessionInfo | null>(null);
const currentQuestion = ref<MockInterviewQuestionInfo | null>(null);
const videoRef = ref<HTMLVideoElement | null>(null);
const mediaStream = ref<MediaStream | null>(null);
const mediaRecorder = ref<MediaRecorder | null>(null);
const chunks = ref<Blob[]>([]);
const recording = ref(false);
const recordingSeconds = ref(0);
const recordingStartedAt = ref(0);
const recordingTimer = ref<number | null>(null);

const jobKeyword = ref("");
const loadingJobs = ref(false);
const starting = ref(false);
const submitting = ref(false);
const loadingDetail = ref(false);
const reviewLoading = ref(false);
const review = ref<MockInterviewReviewInfo | null>(null);
const studyPlanLoading = ref(false);
const studyPlan = ref<MockInterviewStudyPlanInfo | null>(null);
const learningPlanGenerating = ref(false);
const interviewError = ref<InterviewErrorState | null>(null);

const form = reactive({ resumeId: "", jobId: "", questionCount: 6, excludeRecentHours: 72 });

const answeredCount = computed(() => session.value?.answers?.length || 0);

const progressPercent = computed(() => {
  if (!session.value?.totalQuestionCount) {
    return 0;
  }
  return Math.min(100, Math.round((answeredCount.value / session.value.totalQuestionCount) * 100));
});

const interviewStageText = computed(() => {
  if (submitting.value) {
    return "识别与评分中";
  }
  if (recording.value) {
    return "录音回答中";
  }
  if (!currentQuestion.value) {
    return "等待复盘";
  }
  return "答题中";
});

const weakQuestionCount = computed(() => {
  return (review.value?.questionReviews || []).filter(item => {
    return item.wrongBook || item.correct === false || Boolean(item.missingPoints?.length);
  }).length;
});

const answersWithQuestion = computed(() => {
  const questionMap = new Map<number, MockInterviewQuestionInfo>();
  (session.value?.questions || []).forEach(item => questionMap.set(item.id, item));
  return (session.value?.answers || []).map((answer: MockInterviewAnswerInfo) => ({
    ...answer,
    questionContent: questionMap.get(answer.questionId)?.questionContent || "-"
  }));
});

function getErrorMessage(error: unknown, fallback: string) {
  return error instanceof Error && error.message ? error.message : fallback;
}

function getBusinessErrorCode(error: unknown) {
  return error instanceof ApiRequestError ? error.errorCode : undefined;
}

function setInterviewError(error: InterviewErrorState) {
  interviewError.value = error;
}

function clearInterviewError() {
  interviewError.value = null;
}

function buildSubmitError(error: unknown): InterviewErrorState {
  const errorCode = getBusinessErrorCode(error);
  const message = getErrorMessage(error, "音频已经提交，但处理没有成功。");

  if (errorCode === "MOCK_INTERVIEW_ASR_FAILED") {
    return {
      step: "ASR",
      title: "语音识别失败",
      message,
      suggestion: "请重新录音，尽量靠近麦克风、保持环境安静，并确认回答音频里有清晰人声。",
      action: "RECORD_AGAIN"
    };
  }

  if (errorCode === "MOCK_INTERVIEW_AUDIO_SUBMIT_FAILED") {
    return {
      step: "AUDIO_SUBMIT",
      title: "音频提交失败",
      message,
      suggestion: "可以先重试提交当前录音；如果仍失败，请检查网络后重新录音。",
      action: chunks.value.length ? "RETRY_SUBMIT" : "RECORD_AGAIN"
    };
  }

  if (errorCode === "MOCK_INTERVIEW_QUESTION_ALREADY_ANSWERED") {
    return {
      step: "QUESTION_ALREADY_ANSWERED",
      title: "这道题已经提交过",
      message,
      suggestion: "请刷新当前面试进度，系统会进入下一道未回答题目。",
      action: "RECORD_AGAIN"
    };
  }

  return {
    step: "ASR_AND_SCORE",
    title: "语音识别或单题评分失败",
    message,
    suggestion: "可以先重试提交当前录音；如果仍失败，请重新录音，尽量靠近麦克风并保持环境安静。",
    action: chunks.value.length ? "RETRY_SUBMIT" : "RECORD_AGAIN"
  };
}

function buildReviewError(error: unknown): InterviewErrorState {
  const errorCode = getBusinessErrorCode(error);
  const message = getErrorMessage(error, "模型没有成功生成本场面试复盘。");

  if (errorCode === "MOCK_INTERVIEW_REVIEW_NO_ANSWER") {
    return {
      step: "REVIEW_NO_ANSWER",
      title: "还没有可复盘的回答",
      message,
      suggestion: "请至少完成一道题并提交回答后，再生成 AI 面试复盘。",
      action: "RECORD_AGAIN"
    };
  }

  if (errorCode === "MOCK_INTERVIEW_REVIEW_JSON_PARSE_FAILED") {
    return {
      step: "REVIEW_JSON_PARSE",
      title: "AI 复盘格式解析失败",
      message,
      suggestion: "请重新生成复盘；如果多次失败，需要检查该场景 Prompt 是否要求模型只输出 JSON。",
      action: "RETRY_REVIEW"
    };
  }

  return {
    step: "REVIEW_GENERATE",
    title: "AI 总复盘生成失败",
    message,
    suggestion: "请稍后重新生成复盘；如果多次失败，需要检查模型路由或模型调用日志。",
    action: "RETRY_REVIEW"
  };
}

function buildStudyMaterialError(error: unknown): InterviewErrorState {
  const errorCode = getBusinessErrorCode(error);
  const message = getErrorMessage(error, "RAG 补课材料暂时没有加载成功。");

  if (errorCode === "MOCK_INTERVIEW_STUDY_PLAN_REVIEW_REQUIRED") {
    return {
      step: "STUDY_PLAN_REVIEW_REQUIRED",
      title: "请先生成 AI 面试总结",
      message,
      suggestion: "补课清单依赖复盘里的薄弱点，请先生成本场 AI 面试总结。",
      action: "RETRY_REVIEW"
    };
  }

  return {
    step: "STUDY_MATERIAL",
    title: "补课清单加载失败",
    message,
    suggestion: "可以重新刷新清单；如果仍失败，请检查 RAG 知识库是否已索引相关面试题材料。",
    action: "RETRY_STUDY_MATERIAL"
  };
}

async function loadInitialData() {
  resumes.value = await listResumes();
  await loadJobs();
  await loadSessionFromRoute();
}

async function loadJobs() {
  loadingJobs.value = true;
  try {
    const page = await pageFrontPositions({ pageNo: 1, pageSize: 20, keyword: jobKeyword.value });
    jobs.value = page.records || [];
  } finally {
    loadingJobs.value = false;
  }
}

async function startInterview() {
  if (!form.resumeId || !form.jobId) {
    ElMessage.warning("请先选择简历和岗位");
    return;
  }
  starting.value = true;
  try {
    clearInterviewError();
    session.value = await startAiInterview(form);
    currentQuestion.value = await getCurrentMockQuestion(session.value.id);
    review.value = null;
    studyPlan.value = null;
    await openCamera();
  } catch (error) {
    if (!interviewError.value) {
      setInterviewError({
        step: "START_INTERVIEW",
        title: "面试创建失败",
        message: getErrorMessage(error, "系统暂时无法创建 AI 模拟面试。"),
        suggestion: "请确认简历和岗位仍然有效，稍后重新点击开始面试。"
      });
    }
    ElMessage.error(getErrorMessage(error, "AI 模拟面试创建失败"));
  } finally {
    starting.value = false;
  }
}

async function openCamera() {
  if (mediaStream.value) return;
  try {
    clearInterviewError();
    // 浏览器要求 HTTPS 或 localhost 才能使用摄像头和麦克风。
    mediaStream.value = await navigator.mediaDevices.getUserMedia({ video: true, audio: true });
    if (videoRef.value) {
      videoRef.value.srcObject = mediaStream.value;
    }
  } catch (error) {
    setInterviewError({
      step: "DEVICE_PERMISSION",
      title: "摄像头或麦克风打开失败",
      message: getErrorMessage(error, "浏览器没有拿到摄像头或麦克风权限。"),
      suggestion: "请检查浏览器权限、设备是否被其他软件占用，然后点击重新打开设备。",
      action: "OPEN_CAMERA"
    });
    throw error;
  }
}

function closeCamera() {
  mediaStream.value?.getTracks().forEach(track => track.stop());
  mediaStream.value = null;
  if (videoRef.value) {
    videoRef.value.srcObject = null;
  }
}

async function startRecording() {
  if (!session.value || !currentQuestion.value) return;
  try {
    clearInterviewError();
    await openCamera();
    if (!mediaStream.value) return;

    chunks.value = [];
    recordingStartedAt.value = Date.now();
    recordingSeconds.value = 0;

    // 只录音频轨道，避免第一版上传大体积视频文件。
    const audioStream = new MediaStream(mediaStream.value.getAudioTracks());
    mediaRecorder.value = new MediaRecorder(audioStream, { mimeType: "audio/webm" });
    mediaRecorder.value.ondataavailable = event => {
      if (event.data.size > 0) chunks.value.push(event.data);
    };
    mediaRecorder.value.onstop = submitCurrentAudio;
    mediaRecorder.value.start();
    recording.value = true;
    recordingTimer.value = window.setInterval(() => {
      recordingSeconds.value = Math.floor((Date.now() - recordingStartedAt.value) / 1000);
    }, 1000);
  } catch (error) {
    if (!interviewError.value) {
      setInterviewError({
        step: "RECORD_AUDIO",
        title: "录音启动失败",
        message: getErrorMessage(error, "浏览器无法启动录音。"),
        suggestion: "请重新授权麦克风，或刷新页面后重新进入这场面试。",
        action: "OPEN_CAMERA"
      });
    }
  }
}

function stopRecording() {
  if (!mediaRecorder.value || mediaRecorder.value.state === "inactive") return;
  mediaRecorder.value.stop();
  recording.value = false;
  if (recordingTimer.value) {
    window.clearInterval(recordingTimer.value);
    recordingTimer.value = null;
  }
}

async function submitCurrentAudio() {
  if (!session.value || !currentQuestion.value) return;
  const audio = new Blob(chunks.value, { type: "audio/webm" });
  if (!audio.size) {
    setInterviewError({
      step: "EMPTY_AUDIO",
      title: "没有录到有效音频",
      message: "本次录音文件为空，系统无法提交给 ASR 识别。",
      suggestion: "请确认麦克风可用，然后重新录音并提交。",
      action: "RECORD_AGAIN"
    });
    ElMessage.warning("没有录到音频，请重新录制");
    return;
  }

  submitting.value = true;
  try {
    clearInterviewError();
    await submitMockAudioAnswer(session.value.id, currentQuestion.value.id, audio, recordingSeconds.value);
    await reloadSession();
    currentQuestion.value = await getCurrentMockQuestion(session.value.id);
    if (!currentQuestion.value) {
      closeCamera();
      await loadLatestReview();
    }
  } catch (error) {
    setInterviewError(buildSubmitError(error));
    ElMessage.error(getErrorMessage(error, "语音识别或单题评分失败"));
  } finally {
    submitting.value = false;
  }
}

async function reloadSession() {
  if (!session.value) return;
  loadingDetail.value = true;
  try {
    session.value = await getMockInterviewDetail(session.value.id);
  } finally {
    loadingDetail.value = false;
  }
}

async function loadSessionFromRoute() {
  const sessionId = normalizeSessionId(route.query.sessionId);

  if (!sessionId) {
    return;
  }

  loadingDetail.value = true;
  try {
    // 深链进入时复用后端已有详情接口，保证问题、答案、状态都来自同一个会话快照。
    session.value = await getMockInterviewDetail(sessionId);
    currentQuestion.value = await getCurrentMockQuestion(sessionId);
    form.resumeId = session.value.resumeId ? String(session.value.resumeId) : form.resumeId;
    form.jobId = session.value.jobId ? String(session.value.jobId) : form.jobId;
    review.value = null;
    studyPlan.value = null;
    await loadLatestReview();
  } catch (error) {
    session.value = null;
    currentQuestion.value = null;
    ElMessage.error(error instanceof Error ? error.message : "加载 AI 模拟面试会话失败");
  } finally {
    loadingDetail.value = false;
  }
}

function normalizeSessionId(value: unknown) {
  if (Array.isArray(value)) {
    return value[0] || "";
  }

  return typeof value === "string" ? value : "";
}

async function loadLatestReview() {
  if (!session.value) return;
  try {
    review.value = await getLatestMockInterviewReview(session.value.id);
    if (review.value) {
      await loadStudyPlan();
    }
  } catch {
    review.value = null;
    studyPlan.value = null;
  }
}

async function generateReview() {
  if (!session.value) return;
  reviewLoading.value = true;
  try {
    clearInterviewError();
    review.value = await generateMockInterviewReview(session.value.id);
    await loadStudyPlan();
    ElMessage.success("AI 面试总结已生成");
  } catch (error) {
    setInterviewError(buildReviewError(error));
    ElMessage.error(getErrorMessage(error, "AI 面试总结生成失败"));
  } finally {
    reviewLoading.value = false;
  }
}

async function loadStudyPlan() {
  if (!session.value || !review.value) return;
  studyPlanLoading.value = true;
  try {
    studyPlan.value = await getMockInterviewStudyPlan(session.value.id);
  } catch (error) {
    studyPlan.value = null;
    setInterviewError(buildStudyMaterialError(error));
    ElMessage.error(getErrorMessage(error, "补课清单加载失败"));
  } finally {
    studyPlanLoading.value = false;
  }
}

async function generateLearningPlanFromReview() {
  if (!review.value) {
    ElMessage.warning("请先生成 AI 面试总结");
    return;
  }
  learningPlanGenerating.value = true;
  try {
    clearInterviewError();
    /*
     * 1. 学习计划接口本身会基于错题本和薄弱知识点生成计划。
     * 2. 面试复盘页只负责提供明确入口，不在前端重复拼接学习计划内容。
     * 3. 生成成功后直接跳转完整学习计划页，让用户继续补课和复测。
     */
    await generateMockInterviewLearningPlan(7);
    ElMessage.success("学习计划已生成");
    await goLearningPlan();
  } catch (error) {
    setInterviewError({
      step: "LEARNING_PLAN",
      title: "学习计划生成失败",
      message: getErrorMessage(error, "系统没有成功生成学习计划。"),
      suggestion: "请确认本场面试已有错题或薄弱知识点，再点击重新生成学习计划。",
      action: "RETRY_STUDY_PLAN"
    });
    ElMessage.error(getErrorMessage(error, "学习计划生成失败"));
  } finally {
    learningPlanGenerating.value = false;
  }
}

function goWrongQuestions() {
  router.push("/wrong-questions");
}

async function goLearningPlan() {
  await router.push("/learning-plan");
}

function resetInterview() {
  session.value = null;
  currentQuestion.value = null;
  review.value = null;
  studyPlan.value = null;
  clearInterviewError();
}

onMounted(loadInitialData);
onUnmounted(closeCamera);

watch(
  () => route.query.sessionId,
  async () => {
    closeCamera();
    await loadSessionFromRoute();
  }
);
</script>

<style scoped>
.ai-interview-page {
  max-width: 1180px;
  margin: 0 auto;
  padding: 32px 20px 40px;
}

.ai-interview-header,
.section-title-row,
.job-search-row,
.camera-actions,
.record-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.ai-interview-header {
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 22px;
}

.ai-interview-header h1 {
  margin: 6px 0 10px;
  font-size: 30px;
  line-height: 1.2;
  color: #0f172a;
}

.ai-interview-header p {
  margin: 0;
  max-width: 760px;
  color: #64748b;
}

.eyebrow {
  margin: 0;
  color: #0ea5a4;
  font-size: 13px;
  font-weight: 700;
}

.start-layout {
  display: grid;
  grid-template-columns: minmax(420px, 0.95fr) minmax(360px, 1.05fr);
  gap: 20px;
  align-items: start;
}

.table-card,
.guide-card,
.camera-panel,
.question-panel {
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #fff;
  box-shadow: 0 8px 24px rgba(15, 23, 42, 0.04);
}

.ai-start-panel {
  padding: 22px;
}

.card-title {
  margin-bottom: 18px;
}

.card-title h2,
.section-title-row h2,
.primary-guide h2 {
  margin: 0 0 8px;
  font-size: 20px;
  color: #0f172a;
}

.card-title p,
.guide-card p,
.primary-guide li {
  margin: 0;
  color: #64748b;
  line-height: 1.7;
}

.start-form :deep(.el-form-item) {
  margin-bottom: 18px;
}

.start-form :deep(.el-select) {
  width: 100%;
}

.job-search-row {
  width: 100%;
}

.dedupe-setting {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.start-button {
  width: 100%;
  margin-top: 4px;
}

.interview-guide {
  display: grid;
  gap: 16px;
}

.guide-card {
  padding: 18px;
}

.primary-guide {
  background: linear-gradient(135deg, #ecfeff 0%, #f8fafc 100%);
}

.primary-guide ul {
  margin: 12px 0 0;
  padding-left: 18px;
}

.guide-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.guide-grid .guide-card {
  min-height: 132px;
}

.guide-card strong {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  margin-bottom: 12px;
  border-radius: 999px;
  background: #0ea5a4;
  color: #fff;
}

.guide-card span {
  display: block;
  margin-bottom: 6px;
  color: #0f172a;
  font-weight: 700;
}

.interview-layout {
  display: grid;
  grid-template-columns: minmax(320px, 420px) minmax(0, 1fr);
  gap: 18px;
  margin-bottom: 18px;
}

.global-error-alert {
  margin-bottom: 18px;
}

.camera-panel,
.question-panel {
  padding: 18px;
}

.interview-error-alert {
  margin-bottom: 16px;
  border-radius: 8px;
}

.interview-error-body {
  display: grid;
  gap: 8px;
  margin-top: 6px;
}

.interview-error-body p {
  margin: 0;
  color: #7f1d1d;
  line-height: 1.6;
}

.interview-error-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 4px;
}

.camera-panel video {
  width: 100%;
  aspect-ratio: 4 / 3;
  border-radius: 8px;
  background: #111827;
  object-fit: cover;
}

.interview-status-card {
  display: grid;
  gap: 12px;
  margin-bottom: 14px;
  padding: 14px;
  border: 1px solid #dbeafe;
  border-radius: 8px;
  background: #eff6ff;
}

.status-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.status-head span,
.status-steps span,
.answer-flow span {
  color: #64748b;
  font-size: 13px;
}

.status-head strong {
  display: block;
  margin-top: 4px;
  color: #0f172a;
  font-size: 22px;
}

.status-steps,
.answer-flow {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.status-steps span,
.answer-flow span {
  padding: 5px 9px;
  border-radius: 999px;
  background: #fff;
}

.status-steps span.active,
.answer-flow span.active {
  color: #b45309;
  background: #fef3c7;
}

.status-steps span.done,
.answer-flow span.done {
  color: #047857;
  background: #d1fae5;
}

.camera-actions,
.record-actions {
  margin-top: 14px;
}

.answer-flow {
  margin-top: 12px;
}

.question-panel h2 {
  margin: 10px 0 14px;
  line-height: 1.6;
  color: #0f172a;
}

.answer-panel {
  padding: 18px;
}

.review-panel {
  padding: 18px;
  margin-bottom: 18px;
}

.review-content {
  display: grid;
  gap: 16px;
}

.review-score {
  display: flex;
  align-items: baseline;
  gap: 12px;
  padding: 16px;
  border-radius: 8px;
  background: #ecfeff;
}

.review-score strong {
  color: #0f766e;
  font-size: 30px;
}

.review-score span {
  color: #0f172a;
  font-weight: 700;
}

.review-score small {
  color: #64748b;
}

.review-action-strip {
  display: grid;
  grid-template-columns: 160px 160px minmax(0, 1fr);
  gap: 12px;
  align-items: stretch;
}

.review-action-strip section,
.review-next-actions {
  padding: 14px;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  background: #f8fafc;
}

.review-action-strip section strong {
  display: block;
  color: #0f766e;
  font-size: 26px;
}

.review-action-strip section span {
  color: #64748b;
}

.review-next-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 10px;
  flex-wrap: wrap;
}

.review-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.review-block {
  padding: 14px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #fff;
}

.review-block.warning {
  background: #fffbeb;
  border-color: #fde68a;
}

.review-block.plan {
  background: #f8fafc;
}

.review-block.study-plan {
  background: #f8fafc;
}

.question-review-section {
  background: #f8fafc;
}

.question-review-list {
  display: grid;
  gap: 14px;
}

.question-review-card {
  padding: 14px;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  background: #fff;
}

.question-review-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 14px;
}

.question-review-head small {
  color: #64748b;
}

.question-review-head h4 {
  margin: 6px 0 0;
  color: #0f172a;
  line-height: 1.6;
}

.question-review-tags,
.point-grid section {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}

.question-answer-grid,
.point-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  margin-top: 12px;
}

.question-answer-grid section,
.point-grid section {
  padding: 10px;
  border-radius: 8px;
  background: #f8fafc;
}

.question-answer-grid strong,
.point-grid strong {
  display: block;
  width: 100%;
  margin-bottom: 6px;
  color: #0f172a;
}

.question-answer-grid p,
.question-review-conclusion {
  margin: 0;
  color: #475569;
  line-height: 1.7;
  white-space: pre-wrap;
}

.question-review-conclusion {
  margin-top: 12px;
}

.review-block h3 {
  margin: 0 0 8px;
  color: #0f172a;
  font-size: 16px;
}

.review-block p,
.review-block li,
.review-block pre {
  margin: 0;
  color: #475569;
  line-height: 1.7;
}

.review-block ul {
  margin: 0;
  padding-left: 18px;
}

.review-block pre {
  white-space: pre-wrap;
  font-family: inherit;
}

.review-tags {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.study-plan-title {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
}

.study-plan-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.study-plan-title p {
  margin: 0;
  color: #64748b;
}

.study-list {
  display: grid;
  gap: 12px;
}

.study-item {
  padding: 12px;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  background: #fff;
}

.study-item h4 {
  margin: 0 0 8px;
  color: #0f172a;
}

.study-materials {
  display: grid;
  gap: 10px;
  margin-top: 10px;
}

.study-material {
  padding: 10px;
  border-radius: 8px;
  background: #f1f5f9;
}

.study-material strong {
  display: block;
  margin-bottom: 6px;
  color: #0f766e;
}

.study-material small {
  color: #64748b;
}

.answer-review-list {
  display: grid;
  gap: 12px;
  margin-top: 14px;
}

.answer-review-card {
  padding: 12px;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  background: #f8fafc;
}

.answer-review-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.answer-review-head strong {
  color: #0f172a;
  line-height: 1.6;
}

.answer-review-card p {
  margin: 8px 0;
  color: #334155;
  line-height: 1.7;
}

.answer-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.missing-list {
  display: grid;
  gap: 4px;
  margin-top: 8px;
  color: #b91c1c;
  font-size: 12px;
}

.section-title-row {
  justify-content: space-between;
  margin-bottom: 14px;
}

.muted {
  color: #64748b;
  line-height: 1.7;
}

@media (max-width: 980px) {
  .start-layout,
  .interview-layout {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 640px) {
  .ai-interview-page {
    padding: 24px 14px 32px;
  }

  .ai-interview-header,
  .section-title-row {
    flex-direction: column;
    align-items: flex-start;
  }

  .guide-grid {
    grid-template-columns: 1fr;
  }

  .review-grid {
    grid-template-columns: 1fr;
  }

  .review-action-strip {
    grid-template-columns: 1fr;
  }

  .review-next-actions {
    justify-content: flex-start;
  }

  .question-review-head,
  .question-answer-grid,
  .point-grid {
    grid-template-columns: 1fr;
  }

  .question-review-head {
    flex-direction: column;
  }

  .study-plan-title {
    flex-direction: column;
  }
}
</style>
