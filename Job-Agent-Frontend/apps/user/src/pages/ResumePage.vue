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
            <div class="resume-title-row">
              <h3>{{ resume.resumeName }}</h3>
              <span v-if="resume.isDefault === 1" class="default-pill">默认简历</span>
            </div>
            <p>{{ resume.fileName }}</p>
            <div class="resume-meta">
              <span>{{ formatFileSize(resume.fileSize) }}</span>
              <span>{{ resume.createTime || "刚刚上传" }}</span>
              <span class="status-pill">{{ formatStatus(resume.status) }}</span>
            </div>
          </div>

          <div class="resume-actions">
            <button class="primary-button" type="button" :disabled="previewingId === resume.id" @click="openResumeDrawer(resume)">
              {{ previewingId === resume.id ? "打开中..." : "查看文件" }}
            </button>
            <button class="secondary-button" type="button" :disabled="parsingId === resume.id" @click="parseResumeContent(resume)">
              {{ parsingId === resume.id ? "解析中..." : "解析简历" }}
            </button>
            <button class="secondary-button" type="button" @click="renameResume(resume)">修改名称</button>
            <button
              class="secondary-button"
              type="button"
              :disabled="resume.isDefault === 1 || defaultingId === resume.id"
              @click="markAsDefault(resume)"
            >
              {{ resume.isDefault === 1 ? "已默认" : "设为默认" }}
            </button>
            <button class="danger-button" type="button" :disabled="deletingId === resume.id" @click="removeResume(resume)">
              {{ deletingId === resume.id ? "删除中..." : "删除" }}
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
          <button class="text-button" type="button" @click="previewDrawerVisible = false">关闭</button>
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
            <p class="paper-tip">Word 文件浏览器不能直接渲染为图片，这里用文档预览卡片展示，可点击下方按钮下载原文件查看完整内容。</p>
          </div>
        </div>

        <footer v-if="previewResume" class="drawer-preview-footer">
          <button class="primary-button" type="button" :disabled="!previewBlobUrl" @click="downloadPreviewFile">
            下载原文件
          </button>
          <button class="secondary-button" type="button" :disabled="parsingId === previewResume.id" @click="parseResumeContent(previewResume)">
            {{ parsingId === previewResume.id ? "解析中..." : "解析简历" }}
          </button>
          <button
            v-if="previewResume.rawText"
            class="secondary-button"
            type="button"
            @click="togglePreviewMode"
          >
            {{ previewMode === "parse" ? "查看原文件" : "查看解析内容" }}
          </button>
          <button class="secondary-button" type="button" @click="renameResume(previewResume)">修改名称</button>
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
  </main>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import {
  deleteResume,
  fetchResumeFile,
  listResumes,
  parseResumeText,
  setDefaultResume,
  updateResumeName,
  uploadResume
} from "../api/resume";
import type { ResumeInfo } from "../api/types";
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

const isPdfPreview = computed(() => {
  // 1. PDF 浏览器可以直接预览；Word 文件没有原生图片渲染能力，所以走纸张预览卡片。
  return Boolean(previewBlobUrl.value)
    && (previewResume.value?.fileType || "").toUpperCase() === "PDF";
});

onMounted(async () => {
  // 1. 页面刷新后先恢复登录用户，再决定是否加载简历列表。
  await authStore.loadMe();
  if (authStore.isLogin) {
    await loadResumeList();
  }
});

onBeforeUnmount(() => {
  // 1. 页面离开时释放 Blob 临时地址，避免浏览器内存一直被占用。
  revokePreviewUrl();
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
    ElMessage.success("简历上传成功");
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : "简历上传失败";
  } finally {
    uploading.value = false;
  }
}

async function openResumeDrawer(resume: ResumeInfo) {
  const resumeId = String(resume.id);

  // 1. 先打开抽屉，再异步读取文件，用户会立即看到反馈。
  previewDrawerVisible.value = true;
  previewMode.value = "file";
  previewResume.value = resume;
  previewingId.value = resumeId;
  previewErrorMessage.value = "";
  revokePreviewUrl();

  try {
    // 2. 文件流仍然走后端接口，后端会检查简历是否属于当前用户。
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
    // 1. 调用后端解析接口，后端会读取 MinIO 文件并把结果写入 rawText 字段。
    const parsedResume = await parseResumeText(resumeId);
    replaceResumeInList(parsedResume);
    previewResume.value = parsedResume;

    // 2. PARSE_FAILED 不是接口异常，而是业务上的解析失败；原因已经在 rawText 中。
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
    // 1. 用 Element Plus prompt 直接收集新名称，避免再额外做一个编辑弹窗。
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

    // 2. 后端会再次校验名称唯一性，前端校验只是为了更快给用户反馈。
    const updatedResume = await updateResumeName(String(resume.id), cleanedName);
    replaceResumeInList(updatedResume);
    if (previewResume.value?.id === updatedResume.id) {
      previewResume.value = updatedResume;
    }
    ElMessage.success("简历名称已修改");
  } catch (error) {
    // 1. 用户点击取消时 Element Plus 会 reject，这种情况不需要显示错误。
    if (error === "cancel" || error === "close") {
      return;
    }
    ElMessage.error(error instanceof Error ? error.message : "简历名称修改失败");
  }
}

async function removeResume(resume: ResumeInfo) {
  try {
    await ElMessageBox.confirm(`确定删除「${resume.resumeName}」吗？删除后列表中将不再显示。`, "删除简历", {
      type: "warning",
      confirmButtonText: "删除",
      cancelButtonText: "取消"
    });

    // 1. 删除是逻辑删除，后端只会把 isDeleted 改为 1。
    deletingId.value = String(resume.id);
    await deleteResume(String(resume.id));
    await loadResumeList();

    // 2. 如果正在预览这份简历，删除后顺手关掉抽屉，避免用户继续操作已删除数据。
    if (previewResume.value?.id === resume.id) {
      previewDrawerVisible.value = false;
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
    // 1. 后端会先取消其它默认简历，再把当前简历设为默认。
    const defaultResume = await setDefaultResume(String(resume.id));
    await loadResumeList();

    // 2. 抽屉里如果正在展示某份简历，也同步它的默认状态，避免抽屉和列表显示不一致。
    if (previewResume.value) {
      const latestPreviewResume = resumes.value.find(item => item.id === previewResume.value?.id);
      previewResume.value = latestPreviewResume || {
        ...previewResume.value,
        isDefault: previewResume.value.id === defaultResume.id ? 1 : 0
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
    // 1. 从解析结果切回原文件时重新读取文件流，PDF 才能正常在 iframe 中预览。
    await openResumeDrawer(previewResume.value);
    return;
  }

  // 2. 从原文件切到解析结果时不需要请求后端，直接展示当前 VO 里的 rawText。
  previewMode.value = "parse";
}

function downloadPreviewFile() {
  if (!previewBlobUrl.value || !previewResume.value) {
    return;
  }

  // 1. 使用当前抽屉已经加载好的 Blob 临时地址，不需要重复请求后端。
  downloadByTemporaryLink(previewBlobUrl.value, previewResume.value.fileName || previewResume.value.resumeName);
}

function clearPreviewFile() {
  // 1. Drawer 完全关闭后清掉当前预览数据，下一次打开时重新加载最新文件。
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
  // 1. 修改名称后保持原列表位置不变，只替换这一条数据。
  resumes.value = resumes.value.map(item => (item.id === resume.id ? resume : item));
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

  // 1. 兼容数据库里已经保存过的旧解析结果，展示前再清理一次图片资源名。
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
  // 1. DOCX 内嵌图片常见格式是 image1.png，也可能带 word/media/ 这种内部路径。
  return /^(?:[\w.-]+[\\/])*image\d+\.(?:png|jpe?g|gif|bmp|webp|tiff?|svg|emf|wmf)$/i.test(line.trim());
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
  // 1. 创建临时下载链接，触发后立刻移除，不污染页面结构。
  const link = document.createElement("a");
  link.href = blobUrl;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
}
</script>
