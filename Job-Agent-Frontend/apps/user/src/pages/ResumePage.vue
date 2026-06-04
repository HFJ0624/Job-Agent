<template>
  <main class="page-section">
    <section v-if="!authStore.isLogin" class="resume-board">
      <div>
        <p class="eyebrow">我的简历</p>
        <h1>请先登录</h1>
        <p>登录后可以上传多份简历，并查看自己的简历列表。</p>
      </div>
      <RouterLink class="primary-button large" to="/login?redirect=/resume">去登录</RouterLink>
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
          <input v-model.trim="resumeName" maxlength="128" placeholder="例如 Java 后端开发简历" />
        </label>

        <div class="resume-file-picker">
          <input
            ref="resumeInput"
            class="hidden-file-input"
            type="file"
            accept=".pdf,.doc,.docx"
            @change="changeResumeFile"
          />
          <button class="upload-button" type="button" :disabled="uploading" @click="openResumePicker">选择文件</button>
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
        <button class="text-button" type="button" :disabled="loading" @click="loadResumeList">刷新</button>
      </div>

      <p v-if="loading" class="empty-state">正在加载简历列表...</p>
      <p v-else-if="!resumes.length" class="empty-state">还没有上传简历，选择文件后点击上传即可创建第一份。</p>

      <div v-else class="resume-list">
        <article v-for="resume in resumes" :key="resume.id" class="resume-item">
          <div class="resume-file-icon">{{ resume.fileType || "CV" }}</div>
          <div class="resume-info">
            <h3>{{ resume.resumeName }}</h3>
            <p>{{ resume.fileName }}</p>
            <div class="resume-meta">
              <span>{{ formatFileSize(resume.fileSize) }}</span>
              <span>{{ resume.createTime || "刚刚上传" }}</span>
              <span class="status-pill">{{ formatStatus(resume.status) }}</span>
            </div>
          </div>
          <button class="primary-button" type="button" :disabled="openingId === resume.id" @click="openResumeFile(resume)">
            {{ openingId === resume.id ? "打开中..." : "查看文件" }}
          </button>
        </article>
      </div>
    </section>
  </main>
</template>

<script setup lang="ts">
import { onMounted, ref } from "vue";
import { downloadResumeFile, listResumes, uploadResume } from "../api/resume";
import type { ResumeInfo } from "../api/types";
import { useAuthStore } from "../stores/auth";

const authStore = useAuthStore();
const resumeInput = ref<HTMLInputElement | null>(null);
const resumes = ref<ResumeInfo[]>([]);
const selectedFile = ref<File | null>(null);
const resumeName = ref("");
const loading = ref(false);
const uploading = ref(false);
const openingId = ref<string | null>(null);
const errorMessage = ref("");
const successMessage = ref("");

onMounted(async () => {
  // 1. 页面刷新后先恢复登录用户，再决定是否加载简历列表。
  await authStore.loadMe();
  if (authStore.isLogin) {
    await loadResumeList();
  }
});

function openResumePicker() {
  // 1. 通过自定义按钮触发隐藏的文件选择框，页面样式更统一。
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

  // 1. 文件校验通过后暂存文件；如果用户没填名称，就默认使用去掉扩展名的文件名。
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
    // 1. 只查询当前登录用户自己的简历列表，用户 ID 由后端从 token 中读取。
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
    // 1. 上传成功后把新简历插入列表顶部，不需要用户手动刷新。
    const savedResume = await uploadResume({
      resumeName: cleanedName,
      file: selectedFile.value
    });
    resumes.value = [savedResume, ...resumes.value.filter(item => item.id !== savedResume.id)];
    selectedFile.value = null;
    resumeName.value = "";
    if (resumeInput.value) {
      resumeInput.value.value = "";
    }
    successMessage.value = "简历上传成功";
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : "简历上传失败";
  } finally {
    uploading.value = false;
  }
}

async function openResumeFile(resume: ResumeInfo) {
  const resumeId = String(resume.id);

  // 1. 先打开空窗口，避免异步 fetch 完成后被浏览器拦截弹窗。
  const previewWindow = window.open("", "_blank");
  openingId.value = resumeId;
  errorMessage.value = "";
  successMessage.value = "";

  try {
    // 2. 通过后端接口读取 MinIO 文件流，后端会校验当前用户是否有权访问。
    const blob = await downloadResumeFile(resumeId);
    const blobUrl = URL.createObjectURL(blob);

    if (previewWindow) {
      previewWindow.location.href = blobUrl;
    } else {
      downloadByTemporaryLink(blobUrl, resume.fileName || resume.resumeName);
    }

    // 3. 临时地址只用于本次预览，延迟释放，避免刚打开页面就被回收。
    window.setTimeout(() => URL.revokeObjectURL(blobUrl), 60 * 1000);
  } catch (error) {
    previewWindow?.close();
    errorMessage.value = error instanceof Error ? error.message : "简历文件打开失败";
  } finally {
    openingId.value = null;
  }
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

function formatStatus(status?: string) {
  const statusMap: Record<string, string> = {
    UPLOADED: "已上传",
    PARSING: "解析中",
    PARSED: "已解析",
    PARSE_FAILED: "解析失败"
  };
  return statusMap[status || ""] || "已上传";
}

function downloadByTemporaryLink(blobUrl: string, filename: string) {
  // 1. 如果浏览器阻止新窗口，就退一步创建临时下载链接。
  const link = document.createElement("a");
  link.href = blobUrl;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
}
</script>
