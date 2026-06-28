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
        <video ref="videoRef" autoplay playsinline muted></video>
        <div class="camera-actions">
          <el-button @click="openCamera">打开摄像头和麦克风</el-button>
          <el-button @click="closeCamera">关闭设备</el-button>
        </div>
        <p class="muted">第一版只做摄像头预览，不保存整段视频；每道题只上传回答音频。</p>
      </div>

      <div class="question-panel">
        <div v-if="currentQuestion">
          <p class="eyebrow">第 {{ session.currentIndex + 1 }} / {{ session.totalQuestionCount }} 题</p>
          <h2>{{ currentQuestion.questionContent }}</h2>
          <el-tag>{{ currentQuestion.questionType }}</el-tag>

          <div class="record-actions">
            <el-button v-if="!recording" type="danger" :disabled="submitting" @click="startRecording">开始录音</el-button>
            <el-button v-else type="warning" @click="stopRecording">停止并提交</el-button>
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

        <section class="review-block study-plan">
          <div class="study-plan-title">
            <div>
              <h3>薄弱知识点补课清单</h3>
              <p>根据 AI 总结里的薄弱点，从 RAG 知识库召回学习材料。</p>
            </div>
            <el-button size="small" :loading="studyPlanLoading" @click="loadStudyPlan">刷新清单</el-button>
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
import { useRoute } from "vue-router";
import { pageFrontPositions } from "../api/job";
import { listResumes } from "../api/resume";
import { getCurrentMockQuestion, getMockInterviewDetail, startAiInterview, submitMockAudioAnswer } from "../api/mockInterview";
import { generateMockInterviewReview, getLatestMockInterviewReview, getMockInterviewStudyPlan } from "../api/mockInterviewReview";
import type { MockInterviewAnswerInfo, MockInterviewQuestionInfo, MockInterviewReviewInfo, MockInterviewSessionInfo, MockInterviewStudyPlanInfo, PositionInfo, ResumeInfo } from "../api/types";

const route = useRoute();
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

const form = reactive({ resumeId: "", jobId: "", questionCount: 6, excludeRecentHours: 72 });

const answersWithQuestion = computed(() => {
  const questionMap = new Map<number, MockInterviewQuestionInfo>();
  (session.value?.questions || []).forEach(item => questionMap.set(item.id, item));
  return (session.value?.answers || []).map((answer: MockInterviewAnswerInfo) => ({
    ...answer,
    questionContent: questionMap.get(answer.questionId)?.questionContent || "-"
  }));
});

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
    session.value = await startAiInterview(form);
    currentQuestion.value = await getCurrentMockQuestion(session.value.id);
    review.value = null;
    studyPlan.value = null;
    await openCamera();
  } finally {
    starting.value = false;
  }
}

async function openCamera() {
  if (mediaStream.value) return;
  // 浏览器要求 HTTPS 或 localhost 才能使用摄像头和麦克风。
  mediaStream.value = await navigator.mediaDevices.getUserMedia({ video: true, audio: true });
  if (videoRef.value) {
    videoRef.value.srcObject = mediaStream.value;
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
    ElMessage.warning("没有录到音频，请重新录制");
    return;
  }

  submitting.value = true;
  try {
    await submitMockAudioAnswer(session.value.id, currentQuestion.value.id, audio, recordingSeconds.value);
    await reloadSession();
    currentQuestion.value = await getCurrentMockQuestion(session.value.id);
    if (!currentQuestion.value) {
      closeCamera();
      await loadLatestReview();
    }
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
    review.value = await generateMockInterviewReview(session.value.id);
    await loadStudyPlan();
    ElMessage.success("AI 面试总结已生成");
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : "AI 面试总结生成失败");
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
    ElMessage.error(error instanceof Error ? error.message : "补课清单加载失败");
  } finally {
    studyPlanLoading.value = false;
  }
}

function resetInterview() {
  session.value = null;
  currentQuestion.value = null;
  review.value = null;
  studyPlan.value = null;
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

.camera-panel,
.question-panel {
  padding: 18px;
}

.camera-panel video {
  width: 100%;
  aspect-ratio: 4 / 3;
  border-radius: 8px;
  background: #111827;
  object-fit: cover;
}

.camera-actions,
.record-actions {
  margin-top: 14px;
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

  .study-plan-title {
    flex-direction: column;
  }
}
</style>
