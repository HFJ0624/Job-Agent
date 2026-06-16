<template>
  <main class="page-section">
    <section v-if="!authStore.isLogin" class="resume-board">
      <div>
        <p class="eyebrow">我的简历</p>
        <h1>请先登录</h1>
        <p>登录后可以上传多份简历，并查看自己的简历列表。</p>
      </div>
      <RouterLink class="primary-button large" to="/login?redirect=/resume">
        去登录
      </RouterLink>
    </section>

    <section v-else class="resume-board resume-upload-board">
      <div class="resume-board-copy">
        <p class="eyebrow">我的简历</p>
        <h1>上传简历，开启智能匹配</h1>
        <p>系统会解析 PDF / Word 简历，提取技能、项目经历和教育背景，再生成岗位匹配报告。</p>
      </div>

      <form class="resume-upload-panel" @submit.prevent="submitUpload">
        <label class="resume-field">
          <span>简历名称</span>
          <input
            v-model.trim="resumeName"
            maxlength="128"
            placeholder="例如 Java 后端开发简历"
          />
        </label>

        <div class="resume-file-picker">
          <input
            ref="resumeInput"
            class="hidden-file-input"
            type="file"
            accept=".pdf,.doc,.docx"
            @change="changeResumeFile"
          />
          <button
            class="upload-button"
            type="button"
            :disabled="uploading"
            @click="openResumePicker"
          >
            选择文件
          </button>
          <div>
            <strong>{{ selectedFile?.name || "未选择文件" }}</strong>
            <span>支持 PDF、DOC、DOCX，最大 10MB。</span>
          </div>
        </div>

        <p v-if="errorMessage" class="form-error">{{ errorMessage }}</p>
        <p v-if="successMessage" class="form-success">{{ successMessage }}</p>

        <button class="primary-button large" :disabled="uploading">
          {{ uploading ? "上传中..." : "上传简历" }}
        </button>
      </form>
    </section>

    <section class="resume-steps">
      <article>
        <b>1. 文本解析</b>
        <span>抽取简历原文，清洗格式和噪声内容。</span>
      </article>
      <article>
        <b>2. 结构化</b>
        <span>识别技能、项目、教育和工作经历。</span>
      </article>
      <article>
        <b>3. AI 评分</b>
        <span>输出优势、不足和可执行优化建议。</span>
      </article>
    </section>

    <section v-if="authStore.isLogin" class="resume-list-section">
      <div class="section-heading">
        <div>
          <p class="eyebrow">已上传简历</p>
          <h2>我的简历列表</h2>
        </div>
        <button
          class="text-button"
          type="button"
          :disabled="loading"
          @click="loadResumeList"
        >
          刷新
        </button>
      </div>

      <p v-if="loading" class="empty-state">正在加载简历列表...</p>
      <p v-else-if="!resumes.length" class="empty-state">
        还没有上传简历，选择文件后点击上传即可创建第一份。
      </p>

      <div v-else class="resume-list">
        <article
          v-for="resume in resumes"
          :key="resume.id"
          class="resume-item"
        >
          <div class="resume-file-icon">{{ resume.fileType || "CV" }}</div>

          <div class="resume-info">
            <div class="resume-title-row">
              <h3>{{ resume.resumeName }}</h3>
              <span v-if="resume.isDefault === 1" class="default-pill">默认简历</span>
              <span v-if="resume.score !== undefined && resume.score !== null" class="score-pill">
                {{ resume.score }} 分
              </span>
            </div>

            <p>{{ resume.fileName }}</p>

            <div class="resume-meta">
              <span>{{ formatFileSize(resume.fileSize) }}</span>
              <span>{{ resume.createTime || "刚刚上传" }}</span>
              <span class="status-pill">{{ formatStatus(resume.status) }}</span>
            </div>
          </div>

          <div class="resume-actions">
            <button
              class="primary-button"
              type="button"
              :disabled="previewingId === String(resume.id)"
              @click="openResumeDrawer(resume)"
            >
              {{ previewingId === String(resume.id) ? "打开中..." : "查看文件" }}
            </button>

            <button
              class="secondary-button"
              type="button"
              :disabled="parsingId === String(resume.id)"
              @click="parseResumeContent(resume)"
            >
              {{ parsingId === String(resume.id) ? "解析中..." : "解析简历" }}
            </button>

            <button
              class="secondary-button score-button"
              type="button"
              :disabled="scoreLoading || scoringId === String(resume.id)"
              @click="openScoreDrawer(resume)"
            >
              AI评分
            </button>

            <button
              class="secondary-button"
              type="button"
              @click="renameResume(resume)"
            >
              修改名称
            </button>

            <button
              class="secondary-button"
              type="button"
              :disabled="resume.isDefault === 1 || defaultingId === String(resume.id)"
              @click="markAsDefault(resume)"
            >
              {{ resume.isDefault === 1 ? "已默认" : "设为默认" }}
            </button>

            <button
              class="danger-button"
              type="button"
              :disabled="deletingId === String(resume.id)"
              @click="removeResume(resume)"
            >
              {{ deletingId === String(resume.id) ? "删除中..." : "删除" }}
            </button>
          </div>
        </article>
      </div>
    </section>

    <el-drawer
      v-model="previewDrawerVisible"
      direction="rtl"
      size="52%"
      class="resume-preview-drawer"
      :with-header="false"
      @closed="clearPreviewFile"
    >
      <section class="drawer-preview-shell">
        <header class="drawer-preview-header">
          <div>
            <p class="eyebrow">简历预览</p>
            <h2>{{ previewResume?.resumeName || "简历文件" }}</h2>
            <span>{{ previewResume?.fileName }}</span>
          </div>
          <button
            class="text-button"
            type="button"
            @click="previewDrawerVisible = false"
          >
            关闭
          </button>
        </header>

        <p v-if="previewErrorMessage" class="form-error">{{ previewErrorMessage }}</p>
        <p v-else-if="previewingId" class="empty-state">正在读取简历文件...</p>

        <div v-else-if="previewResume" class="resume-preview-body">
          <div
            v-if="previewMode === 'parse'"
            class="resume-parse-preview"
            :class="{ failed: previewResume.status === 'PARSE_FAILED' }"
          >
            <div class="parse-preview-header">
              <span>{{ previewResume.status === "PARSE_FAILED" ? "解析失败" : "解析结果" }}</span>
              <strong>{{ previewResume.resumeName }}</strong>
            </div>
            <pre>{{ formatParsedText(previewResume.rawText) || "暂无解析内容，请先点击解析简历。" }}</pre>
          </div>

          <iframe
            v-else-if="isPdfPreview"
            class="resume-pdf-frame"
            :src="previewBlobUrl"
            title="简历 PDF 预览"
          ></iframe>

          <div v-else class="resume-paper-preview">
            <div class="paper-top-line"></div>
            <div class="paper-avatar">{{ previewResume.fileType || "CV" }}</div>
            <h3>{{ previewResume.resumeName }}</h3>
            <p>{{ previewResume.fileName }}</p>
            <div class="paper-line wide"></div>
            <div class="paper-line"></div>
            <div class="paper-line short"></div>

            <div class="paper-section">
              <b>文件信息</b>
              <span>类型：{{ previewResume.fileType }}</span>
              <span>大小：{{ formatFileSize(previewResume.fileSize) }}</span>
              <span>上传时间：{{ previewResume.createTime || "-" }}</span>
            </div>

            <p class="paper-tip">
              Word 文件浏览器不能直接渲染为图片，这里用文档预览卡片展示，可点击下方按钮下载原文件查看完整内容。
            </p>
          </div>
        </div>

        <footer v-if="previewResume" class="drawer-preview-footer">
          <button
            class="primary-button"
            type="button"
            :disabled="!previewBlobUrl"
            @click="downloadPreviewFile"
          >
            下载原文件
          </button>

          <button
            class="secondary-button"
            type="button"
            :disabled="parsingId === String(previewResume.id)"
            @click="parseResumeContent(previewResume)"
          >
            {{ parsingId === String(previewResume.id) ? "解析中..." : "解析简历" }}
          </button>

          <button
            class="secondary-button score-button"
            type="button"
            @click="openScoreDrawer(previewResume)"
          >
            AI评分
          </button>

          <button
            v-if="previewResume.rawText"
            class="secondary-button"
            type="button"
            @click="togglePreviewMode"
          >
            {{ previewMode === "parse" ? "查看原文件" : "查看解析内容" }}
          </button>

          <button
            class="secondary-button"
            type="button"
            @click="renameResume(previewResume)"
          >
            修改名称
          </button>

          <button
            class="secondary-button"
            type="button"
            :disabled="previewResume.isDefault === 1"
            @click="markAsDefault(previewResume)"
          >
            {{ previewResume.isDefault === 1 ? "已是默认简历" : "设为默认简历" }}
          </button>
        </footer>
      </section>
    </el-drawer>

    <el-drawer
      v-model="scoreDrawerVisible"
      direction="rtl"
      size="46%"
      class="resume-score-drawer"
      :with-header="false"
      @closed="clearScoreDrawer"
    >
      <section class="score-drawer-shell">
        <header class="score-drawer-header">
          <div>
            <p class="eyebrow">AI 简历评分</p>
            <h2>{{ scoreResumeTarget?.resumeName || "简历评分" }}</h2>
            <span>{{ scoreResumeTarget?.fileName }}</span>
          </div>

          <button
            class="text-button"
            type="button"
            @click="scoreDrawerVisible = false"
          >
            关闭
          </button>
        </header>

        <div v-if="scoreResumeTarget" class="score-input-card">
          <label class="resume-field">
            <span>求职方向</span>
            <input
              v-model.trim="scoreTargetPosition"
              maxlength="128"
              placeholder="例如 Java 后端开发、AI Agent 开发，可不填"
            />
          </label>

          <div class="score-action-row">
            <button
              class="primary-button large"
              type="button"
              :disabled="scoringId === String(scoreResumeTarget.id)"
              @click="submitScoreResume"
            >
              {{ scoringId === String(scoreResumeTarget.id) ? "评分中..." : scoreResult ? "重新评分" : "开始评分" }}
            </button>

            <button
              class="secondary-button"
              type="button"
              :disabled="scoreLoading"
              @click="loadLatestScore(scoreResumeTarget)"
            >
              {{ scoreLoading ? "加载中..." : "刷新结果" }}
            </button>
          </div>

          <p class="score-tip">
            V2 评分会等待大模型参与八维打分和证据化分析；规则引擎只作为稳定初始分和异常兜底。
          </p>
        </div>

        <p v-if="scoreErrorMessage" class="form-error">{{ scoreErrorMessage }}</p>
        <p v-if="scoreLoading" class="empty-state">正在加载评分结果...</p>

        <el-empty
          v-if="!scoreLoading && !scoreResult"
          description="暂无评分结果，请点击开始评分"
        />

        <div v-if="scoreResult" class="score-result">
          <div class="total-score-card">
            <div class="score-number">{{ scoreResult.overallScore ?? scoreResult.totalScore }}</div>
            <div class="score-meta">
              <strong>{{ scoreResult.level }}</strong>
              <span>评分时间：{{ scoreResult.createTime || "-" }}</span>
              <small>{{ scoreResult.scoreVersion || "V1" }} · {{ formatLlmStatus(scoreResult.llmStatus) }}</small>
              <em v-if="scoreResult.llmError">{{ scoreResult.llmError }}</em>
            </div>
          </div>

          <div class="dimension-grid">
            <div
              v-for="dimension in normalizedScoreDimensions"
              :key="dimension.dimensionName"
              class="dimension-card dimension-card-detail"
            >
              <div>
                <span>{{ dimension.dimensionName }}</span>
                <p v-if="dimension.reason">{{ dimension.reason }}</p>
              </div>
              <strong>{{ dimension.score }}/{{ dimension.maxScore }}</strong>
            </div>
          </div>

          <div v-if="scoreResult.summary" class="analysis-card summary">
            <h3>整体总结</h3>
            <p>{{ scoreResult.summary }}</p>
          </div>

          <div class="analysis-card">
            <h3>简历优势</h3>
            <ul v-if="scoreStrengths.length">
              <li v-for="item in scoreStrengths" :key="item">{{ item }}</li>
            </ul>
            <p v-else>暂无优势分析。</p>
          </div>

          <div class="analysis-card warning">
            <h3>存在问题</h3>
            <ul v-if="scoreWeaknesses.length">
              <li v-for="item in scoreWeaknesses" :key="item">{{ item }}</li>
            </ul>
            <p v-else>暂无明显问题。</p>
          </div>

          <div class="analysis-card risk">
            <h3>风险点</h3>
            <ul v-if="scoreRisks.length">
              <li v-for="item in scoreRisks" :key="item">{{ item }}</li>
            </ul>
            <p v-else>暂无明显高风险点。</p>
          </div>

          <div class="analysis-card suggestion">
            <h3>优化建议</h3>
            <ul v-if="scoreSuggestions.length">
              <li v-for="item in scoreSuggestions" :key="item">{{ item }}</li>
            </ul>
            <p v-else>暂无优化建议。</p>
          </div>
        </div>
      </section>
    </el-drawer>
  </main>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from "vue";
import { RouterLink } from "vue-router";
import { ElMessage, ElMessageBox } from "element-plus";
import {
  deleteResume,
  fetchResumeFile,
  getLatestResumeScore,
  listResumes,
  parseResumeText,
  scoreResume,
  setDefaultResume,
  updateResumeName,
  uploadResume
} from "../api/resume";
import type { ResumeInfo, ResumeScoreDimensionInfo, ResumeScoreInfo } from "../api/types";
import { useAuthStore } from "../stores/auth";

const authStore = useAuthStore();

const resumeInput = ref<HTMLInputElement | null>(null);
const resumes = ref<ResumeInfo[]>([]);
const selectedFile = ref<File | null>(null);
const resumeName = ref("");

const loading = ref(false);
const uploading = ref(false);

const previewingId = ref<string | null>(null);
const parsingId = ref<string | null>(null);
const deletingId = ref<string | null>(null);
const defaultingId = ref<string | null>(null);

const errorMessage = ref("");
const successMessage = ref("");

const previewDrawerVisible = ref(false);
const previewResume = ref<ResumeInfo | null>(null);
const previewBlobUrl = ref("");
const previewContentType = ref("");
const previewErrorMessage = ref("");
const previewMode = ref<"file" | "parse">("file");

const scoreDrawerVisible = ref(false);
const scoreResumeTarget = ref<ResumeInfo | null>(null);
const scoreResult = ref<ResumeScoreInfo | null>(null);
const scoreTargetPosition = ref("");
const scoreLoading = ref(false);
const scoringId = ref<string | null>(null);
const scoreErrorMessage = ref("");

const SCORE_POLL_INTERVAL_MS = 3000;
const SCORE_POLL_MAX_ATTEMPTS = 20;
let scorePollTimer: ReturnType<typeof window.setTimeout> | null = null;
let scorePollAttempts = 0;

const isPdfPreview = computed(() => {
  return Boolean(previewBlobUrl.value)
    && (previewResume.value?.fileType || "").toUpperCase() === "PDF";
});

const normalizedScoreDimensions = computed<ResumeScoreDimensionInfo[]>(() => {
  const score = scoreResult.value;

  if (!score) {
    return [];
  }

  if (score.dimensions?.length) {
    return score.dimensions;
  }

  if (score.scoreBreakdown) {
    return [
      buildDimension("基础信息完整性", score.scoreBreakdown.basicInfoScore, 10),
      buildDimension("求职目标清晰度", score.scoreBreakdown.careerGoalScore, 10),
      buildDimension("教育背景", score.scoreBreakdown.educationScore, 10),
      buildDimension("技能结构", score.scoreBreakdown.skillsScore, 15),
      buildDimension("项目经历质量", score.scoreBreakdown.projectExperienceScore, 25),
      buildDimension("实习 / 工作经历", score.scoreBreakdown.workExperienceScore, 15),
      buildDimension("成果量化程度", score.scoreBreakdown.quantifiedImpactScore, 10),
      buildDimension("表达与排版", score.scoreBreakdown.formatScore, 5)
    ];
  }

  return [
    buildDimension("基础信息", score.basicInfoScore, 10),
    buildDimension("教育背景", score.educationScore, 10),
    buildDimension("技能栈", score.skillScore, 20),
    buildDimension("项目经历", score.projectScore, 35),
    buildDimension("工作经历", score.experienceScore, 15),
    buildDimension("表达质量", score.expressionScore, 10)
  ];
});

const scoreStrengths = computed(() => {
  return normalizeScoreList(scoreResult.value?.strengths, scoreResult.value?.advantages);
});

const scoreWeaknesses = computed(() => {
  return normalizeScoreList(scoreResult.value?.weaknesses, scoreResult.value?.problems);
});

const scoreRisks = computed(() => {
  return normalizeScoreList(scoreResult.value?.riskPoints);
});

const scoreSuggestions = computed(() => {
  return normalizeScoreList(scoreResult.value?.improvementSuggestions, scoreResult.value?.suggestions);
});

onMounted(async () => {
  await authStore.loadMe();
  if (authStore.isLogin) {
    await loadResumeList();
  }
});

onBeforeUnmount(() => {
  revokePreviewUrl();
  clearScorePollTimer();
});

function buildDimension(dimensionName: string, score = 0, maxScore = 0): ResumeScoreDimensionInfo {
  return {
    dimensionName,
    score,
    maxScore,
    reason: "历史评分记录未保存该维度的详细解释。"
  };
}

function normalizeScoreList(primary?: string[], fallback?: string[]) {
  const source = primary?.length ? primary : fallback;

  if (!source?.length) {
    return [];
  }

  return source
    .map(item => item.trim())
    .filter(Boolean);
}

function formatLlmStatus(status?: string) {
  const statusMap: Record<string, string> = {
    PROCESSING: "AI 评分中",
    SUCCESS: "AI 已参与评分",
    FAILED: "AI 评分失败",
    SKIPPED: "AI 未启用"
  };

  return statusMap[status || ""] || "历史评分";
}

function clearScorePollTimer() {
  if (scorePollTimer) {
    window.clearTimeout(scorePollTimer);
    scorePollTimer = null;
  }
}

function scheduleScorePollingIfNeeded(resume = scoreResumeTarget.value) {
  clearScorePollTimer();

  if (!resume?.id || !scoreDrawerVisible.value || scoreResult.value?.llmStatus !== "PROCESSING") {
    return;
  }

  if (scorePollAttempts >= SCORE_POLL_MAX_ATTEMPTS) {
    return;
  }

  scorePollAttempts += 1;
  scorePollTimer = window.setTimeout(() => {
    void loadLatestScore(resume, true);
  }, SCORE_POLL_INTERVAL_MS);
}

function openResumePicker() {
  if (!uploading.value) {
    resumeInput.value?.click();
  }
}

function changeResumeFile(event: Event) {
  const input = event.target as HTMLInputElement;
  const file = input.files?.[0];

  if (!file) {
    return;
  }

  if (!validateResumeFile(file)) {
    input.value = "";
    return;
  }

  selectedFile.value = file;

  if (!resumeName.value) {
    resumeName.value = removeExtension(file.name);
  }

  errorMessage.value = "";
  successMessage.value = "";
}

async function loadResumeList() {
  loading.value = true;
  errorMessage.value = "";

  try {
    resumes.value = await listResumes();
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : "简历列表加载失败";
  } finally {
    loading.value = false;
  }
}

async function submitUpload() {
  const cleanedName = resumeName.value.trim();

  if (!cleanedName) {
    errorMessage.value = "请填写简历名称";
    return;
  }

  if (!selectedFile.value) {
    errorMessage.value = "请选择要上传的简历文件";
    return;
  }

  if (resumes.value.some(item => item.resumeName === cleanedName)) {
    errorMessage.value = "简历名称已经存在，请换一个名称";
    return;
  }

  uploading.value = true;
  errorMessage.value = "";
  successMessage.value = "";

  try {
    const savedResume = await uploadResume({
      resumeName: cleanedName,
      file: selectedFile.value
    });

    resumes.value = [
      savedResume,
      ...resumes.value.filter(item => item.id !== savedResume.id)
    ];

    selectedFile.value = null;
    resumeName.value = "";

    if (resumeInput.value) {
      resumeInput.value.value = "";
    }

    successMessage.value = "简历上传成功";
    ElMessage.success("简历上传成功");
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : "简历上传失败";
  } finally {
    uploading.value = false;
  }
}

async function openResumeDrawer(resume: ResumeInfo) {
  const resumeId = String(resume.id);

  previewDrawerVisible.value = true;
  previewMode.value = "file";
  previewResume.value = resume;
  previewingId.value = resumeId;
  previewErrorMessage.value = "";
  revokePreviewUrl();

  try {
    const file = await fetchResumeFile(resumeId);
    previewBlobUrl.value = URL.createObjectURL(file.blob);
    previewContentType.value = file.contentType;
  } catch (error) {
    previewErrorMessage.value = error instanceof Error ? error.message : "简历文件读取失败";
  } finally {
    previewingId.value = null;
  }
}

async function parseResumeContent(resume: ResumeInfo) {
  const resumeId = String(resume.id);

  parsingId.value = resumeId;
  previewDrawerVisible.value = true;
  previewMode.value = "parse";
  previewResume.value = resume;
  previewErrorMessage.value = "";
  revokePreviewUrl();

  try {
    const parsedResume = await parseResumeText(resumeId);
    replaceResumeInList(parsedResume);
    previewResume.value = parsedResume;

    if (scoreResumeTarget.value?.id === parsedResume.id) {
      scoreResumeTarget.value = parsedResume;
    }

    if (parsedResume.status === "PARSE_FAILED") {
      ElMessage.error("简历解析失败，原因已展示在抽屉中");
    } else {
      ElMessage.success("简历解析完成");
    }
  } catch (error) {
    previewErrorMessage.value = error instanceof Error ? error.message : "简历解析失败";
    ElMessage.error(previewErrorMessage.value);
  } finally {
    parsingId.value = null;
  }
}

async function renameResume(resume: ResumeInfo) {
  try {
    const { value } = await ElMessageBox.prompt("请输入新的简历名称", "修改简历名称", {
      inputValue: resume.resumeName,
      inputPattern: /^.{1,128}$/,
      inputErrorMessage: "简历名称不能为空，且不能超过128位",
      confirmButtonText: "保存",
      cancelButtonText: "取消"
    });

    const cleanedName = String(value || "").trim();

    if (!cleanedName) {
      ElMessage.warning("简历名称不能为空");
      return;
    }

    if (resumes.value.some(item => item.id !== resume.id && item.resumeName === cleanedName)) {
      ElMessage.warning("简历名称已经存在，请换一个名称");
      return;
    }

    const updatedResume = await updateResumeName(String(resume.id), cleanedName);
    replaceResumeInList(updatedResume);

    if (previewResume.value?.id === updatedResume.id) {
      previewResume.value = updatedResume;
    }

    if (scoreResumeTarget.value?.id === updatedResume.id) {
      scoreResumeTarget.value = updatedResume;
    }

    ElMessage.success("简历名称已修改");
  } catch (error) {
    if (error === "cancel" || error === "close") {
      return;
    }

    ElMessage.error(error instanceof Error ? error.message : "简历名称修改失败");
  }
}

async function removeResume(resume: ResumeInfo) {
  try {
    await ElMessageBox.confirm(
      `确定删除「${resume.resumeName}」吗？删除后列表中将不再显示。`,
      "删除简历",
      {
        type: "warning",
        confirmButtonText: "删除",
        cancelButtonText: "取消"
      }
    );

    deletingId.value = String(resume.id);

    await deleteResume(String(resume.id));
    await loadResumeList();

    if (previewResume.value?.id === resume.id) {
      previewDrawerVisible.value = false;
    }

    if (scoreResumeTarget.value?.id === resume.id) {
      scoreDrawerVisible.value = false;
    }

    ElMessage.success("简历已删除");
  } catch (error) {
    if (error === "cancel" || error === "close") {
      return;
    }

    ElMessage.error(error instanceof Error ? error.message : "简历删除失败");
  } finally {
    deletingId.value = null;
  }
}

async function markAsDefault(resume: ResumeInfo) {
  if (resume.isDefault === 1) {
    return;
  }

  defaultingId.value = String(resume.id);

  try {
    const defaultResume = await setDefaultResume(String(resume.id));
    await loadResumeList();

    if (previewResume.value) {
      const latestPreviewResume = resumes.value.find(item => item.id === previewResume.value?.id);
      previewResume.value = latestPreviewResume || {
        ...previewResume.value,
        isDefault: previewResume.value.id === defaultResume.id ? 1 : 0
      };
    }

    if (scoreResumeTarget.value) {
      const latestScoreResume = resumes.value.find(item => item.id === scoreResumeTarget.value?.id);
      scoreResumeTarget.value = latestScoreResume || {
        ...scoreResumeTarget.value,
        isDefault: scoreResumeTarget.value.id === defaultResume.id ? 1 : 0
      };
    }

    ElMessage.success("默认简历已设置");
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : "默认简历设置失败");
  } finally {
    defaultingId.value = null;
  }
}

async function togglePreviewMode() {
  if (!previewResume.value) {
    return;
  }

  if (previewMode.value === "parse") {
    await openResumeDrawer(previewResume.value);
    return;
  }

  previewMode.value = "parse";
}

function downloadPreviewFile() {
  if (!previewBlobUrl.value || !previewResume.value) {
    return;
  }

  downloadByTemporaryLink(
    previewBlobUrl.value,
    previewResume.value.fileName || previewResume.value.resumeName
  );
}

function clearPreviewFile() {
  revokePreviewUrl();
  previewResume.value = null;
  previewContentType.value = "";
  previewErrorMessage.value = "";
  previewMode.value = "file";
}

function revokePreviewUrl() {
  if (previewBlobUrl.value) {
    URL.revokeObjectURL(previewBlobUrl.value);
    previewBlobUrl.value = "";
  }
}

function replaceResumeInList(resume: ResumeInfo) {
  resumes.value = resumes.value.map(item => {
    return item.id === resume.id ? resume : item;
  });
}

async function openScoreDrawer(resume: ResumeInfo) {
  clearScorePollTimer();
  scorePollAttempts = 0;
  scoreDrawerVisible.value = true;
  scoreResumeTarget.value = resume;
  scoreTargetPosition.value = "";
  scoreErrorMessage.value = "";
  scoreResult.value = null;

  await loadLatestScore(resume);
}

async function loadLatestScore(resume: ResumeInfo, fromPolling = false) {
  const resumeId = String(resume.id);

  if (!fromPolling) {
    clearScorePollTimer();
    scorePollAttempts = 0;
    scoreLoading.value = true;
  }
  scoreErrorMessage.value = "";

  try {
    scoreResult.value = await getLatestResumeScore(resumeId);

    if (fromPolling && scoreResult.value?.llmStatus !== "PROCESSING") {
      await loadResumeList();
      const latestResume = resumes.value.find(item => item.id === resume.id);
      if (latestResume) {
        scoreResumeTarget.value = latestResume;
        if (previewResume.value?.id === latestResume.id) {
          previewResume.value = latestResume;
        }
      }
    }
  } catch (error) {
    scoreErrorMessage.value = error instanceof Error ? error.message : "评分结果加载失败";
  } finally {
    if (!fromPolling) {
      scoreLoading.value = false;
    }
    scheduleScorePollingIfNeeded(resume);
  }
}

async function submitScoreResume() {
  if (!scoreResumeTarget.value) {
    ElMessage.warning("请先选择一份简历");
    return;
  }

  const resumeId = String(scoreResumeTarget.value.id);

  scoringId.value = resumeId;
  scoreErrorMessage.value = "";

  try {
    const result = await scoreResume(resumeId, scoreTargetPosition.value);
    scoreResult.value = result;
    scorePollAttempts = 0;

    await loadResumeList();

    const latestResume = resumes.value.find(item => item.id === scoreResumeTarget.value?.id);
    if (latestResume) {
      scoreResumeTarget.value = latestResume;
      if (previewResume.value?.id === latestResume.id) {
        previewResume.value = latestResume;
      }
    }

    scheduleScorePollingIfNeeded(scoreResumeTarget.value);
    if (result.llmStatus === "SUCCESS") {
      ElMessage.success("AI 已参与简历评分");
    } else if (result.llmStatus === "FAILED" || result.llmStatus === "SKIPPED") {
      ElMessage.warning(result.llmError || "AI 没有参与成功，当前展示规则评分");
    } else {
      ElMessage.success(result.llmStatus === "PROCESSING" ? "AI 评分进行中" : "简历评分完成");
    }
  } catch (error) {
    scoreErrorMessage.value = error instanceof Error ? error.message : "简历评分失败";
    ElMessage.error(scoreErrorMessage.value);
  } finally {
    scoringId.value = null;
  }
}

function clearScoreDrawer() {
  clearScorePollTimer();
  scorePollAttempts = 0;
  scoreResumeTarget.value = null;
  scoreResult.value = null;
  scoreTargetPosition.value = "";
  scoreErrorMessage.value = "";
  scoreLoading.value = false;
  scoringId.value = null;
}

function validateResumeFile(file: File) {
  const extension = getExtension(file.name);

  if (!["pdf", "doc", "docx"].includes(extension)) {
    errorMessage.value = "简历只支持 PDF、DOC、DOCX 格式";
    return false;
  }

  if (file.size > 10 * 1024 * 1024) {
    errorMessage.value = "简历文件不能超过10MB";
    return false;
  }

  return true;
}

function removeExtension(filename: string) {
  const dotIndex = filename.lastIndexOf(".");

  if (dotIndex <= 0) {
    return filename;
  }

  return filename.slice(0, dotIndex);
}

function getExtension(filename: string) {
  const dotIndex = filename.lastIndexOf(".");

  if (dotIndex < 0) {
    return "";
  }

  return filename.slice(dotIndex + 1).toLowerCase();
}

function formatFileSize(size?: number) {
  if (!size) {
    return "0B";
  }

  if (size < 1024 * 1024) {
    return `${(size / 1024).toFixed(1)}KB`;
  }

  return `${(size / 1024 / 1024).toFixed(1)}MB`;
}

function formatParsedText(text?: string) {
  if (!text) {
    return "";
  }

  return text
    .replace(/\r\n/g, "\n")
    .replace(/\r/g, "\n")
    .split("\n")
    .filter(line => !isEmbeddedImageLine(line))
    .join("\n")
    .replace(/\n{3,}/g, "\n\n")
    .trim();
}

function isEmbeddedImageLine(line: string) {
  return /^(?:[\w.-]+[\\/])*image\d+\.(?:png|jpe?g|gif|bmp|webp|tiff?|svg|emf|wmf)$/i.test(
    line.trim()
  );
}

function formatStatus(status?: string) {
  const statusMap: Record<string, string> = {
    UPLOADED: "已上传",
    PARSING: "解析中",
    PARSED: "已解析",
    PARSE_FAILED: "解析失败",
    SCORED: "已评分"
  };

  return statusMap[status || ""] || "已上传";
}

function downloadByTemporaryLink(blobUrl: string, filename: string) {
  const link = document.createElement("a");
  link.href = blobUrl;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
}
</script>

<style scoped>
.score-pill {
  display: inline-flex;
  align-items: center;
  padding: 3px 8px;
  border-radius: 999px;
  background: #ecfdf5;
  color: #047857;
  font-size: 12px;
  font-weight: 600;
}

.score-button {
  border-color: #dbeafe;
  color: #2563eb;
  background: #eff6ff;
}

.score-drawer-shell {
  min-height: 100%;
  padding: 24px;
  background: #f8fafc;
}

.score-drawer-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 18px;
}

.score-drawer-header h2 {
  margin: 4px 0 6px;
  color: #111827;
}

.score-drawer-header span {
  color: #6b7280;
  font-size: 13px;
}

.score-input-card {
  padding: 16px;
  margin-bottom: 16px;
  border-radius: 16px;
  background: #ffffff;
  border: 1px solid #e5e7eb;
}

.score-action-row {
  display: flex;
  gap: 12px;
  margin-top: 14px;
}

.score-tip {
  margin: 12px 0 0;
  color: #6b7280;
  font-size: 13px;
  line-height: 1.6;
}

.score-result {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.total-score-card {
  display: flex;
  align-items: center;
  gap: 18px;
  padding: 20px;
  border-radius: 18px;
  background: linear-gradient(135deg, #eff6ff, #ffffff);
  border: 1px solid #bfdbfe;
}

.score-number {
  width: 88px;
  height: 88px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #2563eb;
  color: #ffffff;
  font-size: 28px;
  font-weight: 800;
}

.score-meta {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.score-meta strong {
  color: #111827;
  font-size: 22px;
}

.score-meta span {
  color: #6b7280;
  font-size: 13px;
}

.score-meta small {
  color: #2563eb;
  font-size: 12px;
  font-weight: 700;
}

.score-meta em {
  max-width: 560px;
  padding: 8px 10px;
  border-radius: 8px;
  background: #fff7ed;
  color: #c2410c;
  font-size: 12px;
  font-style: normal;
  line-height: 1.5;
}

.dimension-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.dimension-card {
  padding: 14px;
  border-radius: 14px;
  background: #ffffff;
  border: 1px solid #e5e7eb;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.dimension-card span {
  color: #6b7280;
}

.dimension-card strong {
  color: #111827;
}

.dimension-card-detail {
  align-items: flex-start;
  gap: 12px;
  min-height: 96px;
}

.dimension-card-detail div {
  min-width: 0;
}

.dimension-card-detail p {
  margin: 6px 0 0;
  color: #6b7280;
  font-size: 12px;
  line-height: 1.6;
}

.dimension-card-detail strong {
  flex-shrink: 0;
}

.analysis-card {
  padding: 16px;
  border-radius: 16px;
  background: #ffffff;
  border: 1px solid #e5e7eb;
}

.analysis-card h3 {
  margin: 0 0 10px;
  color: #111827;
  font-size: 16px;
}

.analysis-card ul {
  margin: 0;
  padding-left: 18px;
}

.analysis-card li {
  line-height: 1.8;
  color: #374151;
}

.analysis-card p {
  margin: 0;
  color: #6b7280;
}

.analysis-card.warning {
  border-color: #fed7aa;
  background: #fff7ed;
}

.analysis-card.summary {
  border-color: #bfdbfe;
  background: #eff6ff;
}

.analysis-card.risk {
  border-color: #fecaca;
  background: #fef2f2;
}

.analysis-card.suggestion {
  border-color: #bbf7d0;
  background: #f0fdf4;
}

@media (max-width: 768px) {
  .dimension-grid {
    grid-template-columns: 1fr;
  }

  .score-action-row {
    flex-direction: column;
  }
}
</style>
