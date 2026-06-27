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
            <el-button type="primary" @click="resetInterview">再来一轮</el-button>
          </template>
        </el-result>
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
    </section>
  </main>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref } from "vue";
import { ElMessage } from "element-plus";
import { pageFrontPositions } from "../api/job";
import { listResumes } from "../api/resume";
import { getCurrentMockQuestion, getMockInterviewDetail, startAiInterview, submitMockAudioAnswer } from "../api/mockInterview";
import type { MockInterviewAnswerInfo, MockInterviewQuestionInfo, MockInterviewSessionInfo, PositionInfo, ResumeInfo } from "../api/types";

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

const form = reactive({ resumeId: "", jobId: "", questionCount: 6 });

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

function resetInterview() {
  session.value = null;
  currentQuestion.value = null;
}

onMounted(loadInitialData);
onUnmounted(closeCamera);
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
}
</style>
