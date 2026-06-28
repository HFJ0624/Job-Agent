<template>
  <div class="apply-decision-panel">
    <div class="decision-header">
      <div>
        <h3>AI 投递决策</h3>
        <p>判断这个岗位是否值得投递，并给出风险、简历优化和下一步行动。</p>
      </div>
      <button
        class="primary-button"
        type="button"
        :disabled="!selectedResumeId || deciding"
        @click="handleGenerateDecision"
      >
        {{ deciding ? "决策中..." : decision ? "重新决策" : "生成决策" }}
      </button>
    </div>

    <label class="resume-field">
      <span>选择简历</span>
      <select v-model="selectedResumeId">
        <option value="">请选择简历</option>
        <option v-for="resume in resumes" :key="resume.id" :value="String(resume.id)">
          {{ resume.resumeName }}{{ resume.isDefault === 1 ? "（默认）" : "" }}
        </option>
      </select>
    </label>

    <p v-if="errorMessage" class="form-error">{{ errorMessage }}</p>
    <p v-if="!decision" class="empty-state">暂无投递决策，请选择简历后点击生成。</p>

    <div v-else class="decision-result">
      <div class="decision-score-card" :class="decision.decision.toLowerCase()">
        <div class="decision-score">{{ decision.decisionScore }}</div>
        <div>
          <strong>{{ decision.decisionLabel }}</strong>
          <span>AI 决策 · {{ decision.createTime || "-" }}</span>
        </div>
      </div>

      <section class="decision-block">
        <h4>核心理由</h4>
        <p>{{ decision.reason || "-" }}</p>
      </section>

      <section class="decision-block warning">
        <h4>风险点</h4>
        <ul>
          <li v-for="item in decision.risks" :key="item">{{ item }}</li>
        </ul>
      </section>

      <section class="decision-block">
        <h4>简历优化建议</h4>
        <ul>
          <li v-for="item in decision.resumeSuggestions" :key="item">{{ item }}</li>
        </ul>
      </section>

      <section class="decision-block">
        <h4>面试准备建议</h4>
        <ul>
          <li v-for="item in decision.interviewSuggestions" :key="item">{{ item }}</li>
        </ul>
      </section>

      <section class="decision-block suggestion">
        <h4>下一步行动</h4>
        <ul>
          <li v-for="item in decision.nextActions" :key="item">{{ item }}</li>
        </ul>
      </section>

      <section class="decision-action-panel">
        <div class="action-title">
          <h4>AI 决策后的一键行动</h4>
          <p>根据当前决策结果，直接推进到求职进度、HR 沟通或模拟面试。</p>
        </div>

        <div class="action-buttons">
          <button
            class="primary-button"
            type="button"
            :disabled="actionLoading !== ''"
            @click="handleAddApplication"
          >
            {{ actionLoading === "application" ? "处理中..." : applicationActionText }}
          </button>

          <button
            class="secondary-button"
            type="button"
            :disabled="actionLoading !== ''"
            @click="handleGenerateGreeting"
          >
            {{ actionLoading === "greeting" ? "生成中..." : "生成 HR 打招呼语" }}
          </button>

          <button
            v-if="decision.decision !== 'SKIP'"
            class="secondary-button"
            type="button"
            :disabled="actionLoading !== ''"
            @click="handleStartMockInterview"
          >
            {{ actionLoading === "interview" ? "创建中..." : "开始 AI 模拟面试" }}
          </button>
        </div>

        <div v-if="greetingContent" class="action-output">
          <div class="action-output-header">
            <strong>HR 打招呼语</strong>
            <button class="text-button" type="button" @click="copyGreeting">复制</button>
          </div>
          <p>{{ greetingContent }}</p>
        </div>

        <div v-if="mockSessionId" class="action-output interview-output">
          <strong>AI 模拟面试已创建</strong>
          <p>会话ID：{{ mockSessionId }}。你可以前往 AI 面试页面继续完成摄像头和语音答题流程。</p>
          <button class="text-button" type="button" @click="goToAiInterview">去 AI 面试页</button>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from "vue";
import { ElMessage } from "element-plus";
import { useRouter } from "vue-router";
import { saveApplication } from "../api/application";
import { generateApplyDecision, generateGreeting, getLatestApplyDecision } from "../api/job";
import { startAiInterview } from "../api/mockInterview";
import { listResumes } from "../api/resume";
import type { JobApplyDecisionInfo, ResumeInfo } from "../api/types";

const props = defineProps<{
  jobId: string;
}>();

const router = useRouter();
const resumes = ref<ResumeInfo[]>([]);
const selectedResumeId = ref("");
const decision = ref<JobApplyDecisionInfo | null>(null);
const deciding = ref(false);
const actionLoading = ref("");
const errorMessage = ref("");
const greetingContent = ref("");
const mockSessionId = ref<number | null>(null);

const applicationActionText = computed(() => {
  if (!decision.value) return "加入求职进度";
  if (decision.value.decision === "APPLY") return "加入求职进度并标记已投递";
  if (decision.value.decision === "CAUTIOUS") return "加入待评估进度";
  if (decision.value.decision === "SKIP") return "低优先级收藏到进度";
  return "加入求职进度";
});

onMounted(loadResumes);

async function loadResumes() {
  try {
    resumes.value = await listResumes();
    const defaultResume = resumes.value.find(item => item.isDefault === 1);
    const firstResume = resumes.value[0];
    selectedResumeId.value = String(defaultResume?.id || firstResume?.id || "");
    if (selectedResumeId.value) {
      await loadLatestDecision();
    }
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : "简历列表加载失败";
  }
}

async function loadLatestDecision() {
  greetingContent.value = "";
  mockSessionId.value = null;

  if (!props.jobId || !selectedResumeId.value) {
    decision.value = null;
    return;
  }

  try {
    decision.value = await getLatestApplyDecision(props.jobId, selectedResumeId.value);
  } catch (error) {
    console.error("[Job-Agent] 加载投递决策失败", error);
  }
}

async function handleGenerateDecision() {
  if (!selectedResumeId.value) {
    ElMessage.warning("请先选择一份简历");
    return;
  }

  deciding.value = true;
  errorMessage.value = "";
  greetingContent.value = "";
  mockSessionId.value = null;
  try {
    decision.value = await generateApplyDecision(props.jobId, selectedResumeId.value);
    ElMessage.success("AI 投递决策已生成");
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : "AI 投递决策生成失败";
    ElMessage.error(errorMessage.value);
  } finally {
    deciding.value = false;
  }
}

function requireActionContext() {
  if (!selectedResumeId.value) {
    ElMessage.warning("请先选择一份简历");
    return false;
  }

  if (!decision.value) {
    ElMessage.warning("请先生成 AI 投递决策");
    return false;
  }

  return true;
}

function resolveApplicationAction() {
  const decisionValue = decision.value?.decision;

  // 第一版不新增求职进度状态，直接映射到后端已有状态，避免状态枚举和列表筛选一起扩散修改。
  if (decisionValue === "APPLY") {
    return { status: "APPLIED", priority: "HIGH" };
  }

  if (decisionValue === "SKIP") {
    return { status: "INTERESTED", priority: "LOW" };
  }

  return { status: "INTERESTED", priority: "NORMAL" };
}

async function handleAddApplication() {
  if (!requireActionContext()) return;

  actionLoading.value = "application";
  try {
    const action = resolveApplicationAction();
    await saveApplication({
      jobId: props.jobId,
      resumeId: selectedResumeId.value,
      status: action.status,
      priority: action.priority,
      note: buildDecisionNote()
    });
    ElMessage.success("已加入求职进度");
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : "加入求职进度失败");
  } finally {
    actionLoading.value = "";
  }
}

async function handleGenerateGreeting() {
  if (!requireActionContext()) return;

  actionLoading.value = "greeting";
  try {
    const result = await generateGreeting(props.jobId, selectedResumeId.value, "自然");
    greetingContent.value = result.content;
    ElMessage.success("HR 打招呼语已生成");
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : "生成 HR 打招呼语失败");
  } finally {
    actionLoading.value = "";
  }
}

async function handleStartMockInterview() {
  if (!requireActionContext()) return;

  actionLoading.value = "interview";
  try {
    const session = await startAiInterview({
      jobId: props.jobId,
      resumeId: selectedResumeId.value,
      questionCount: 6,
      excludeRecentHours: 72
    });
    mockSessionId.value = session.id;
    ElMessage.success("AI 模拟面试已创建");
    goToAiInterview();
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : "创建 AI 模拟面试失败");
  } finally {
    actionLoading.value = "";
  }
}

function buildDecisionNote() {
  if (!decision.value) return "";

  const reason = decision.value.reason ? `理由：${decision.value.reason}` : "";
  const risks = decision.value.risks?.length ? `风险：${decision.value.risks.join("；")}` : "";
  return [`AI投递决策：${decision.value.decisionLabel}`, reason, risks]
    .filter(Boolean)
    .join("\n")
    .slice(0, 1000);
}

async function copyGreeting() {
  if (!greetingContent.value) return;

  await navigator.clipboard.writeText(greetingContent.value);
  ElMessage.success("已复制");
}

function goToAiInterview() {
  router.push({
    path: "/ai-interview",
    query: mockSessionId.value ? { sessionId: String(mockSessionId.value) } : undefined
  });
}

watch(selectedResumeId, loadLatestDecision);
</script>

<style scoped>
.apply-decision-panel {
  padding: 18px;
  border-radius: 16px;
  background: #ffffff;
  border: 1px solid #e5e7eb;
}

.decision-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}

.decision-header h3 {
  margin: 0;
  color: #111827;
}

.decision-header p {
  margin: 6px 0 0;
  color: #6b7280;
  font-size: 13px;
}

.decision-result {
  display: grid;
  gap: 14px;
  margin-top: 18px;
}

.decision-score-card {
  display: flex;
  align-items: center;
  gap: 18px;
  padding: 18px;
  border-radius: 16px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
}

.decision-score-card.apply {
  background: #ecfdf5;
  border-color: #bbf7d0;
}

.decision-score-card.cautious {
  background: #fffbeb;
  border-color: #fde68a;
}

.decision-score-card.skip {
  background: #fef2f2;
  border-color: #fecaca;
}

.decision-score {
  width: 82px;
  height: 82px;
  border-radius: 50%;
  background: #0f766e;
  color: #ffffff;
  font-size: 28px;
  font-weight: 800;
  display: flex;
  align-items: center;
  justify-content: center;
}

.decision-score-card strong {
  display: block;
  color: #111827;
  font-size: 22px;
}

.decision-score-card span {
  display: block;
  margin-top: 6px;
  color: #64748b;
  font-size: 13px;
}

.decision-block,
.decision-action-panel {
  padding: 14px;
  border-radius: 14px;
  border: 1px solid #e5e7eb;
  background: #ffffff;
}

.decision-block.warning {
  border-color: #fed7aa;
  background: #fff7ed;
}

.decision-block.suggestion {
  border-color: #bbf7d0;
  background: #f0fdf4;
}

.decision-block h4,
.action-title h4 {
  margin: 0 0 10px;
  color: #111827;
}

.decision-block p,
.decision-block li {
  color: #374151;
  line-height: 1.8;
}

.decision-block p,
.decision-block ul {
  margin: 0;
}

.decision-block ul {
  padding-left: 18px;
}

.decision-action-panel {
  border-color: #bfdbfe;
  background: #eff6ff;
}

.action-title p {
  margin: 0;
  color: #64748b;
  font-size: 13px;
}

.action-buttons {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 12px;
}

.secondary-button,
.text-button {
  border: 1px solid #cbd5e1;
  background: #ffffff;
  color: #334155;
  border-radius: 10px;
  cursor: pointer;
}

.secondary-button {
  padding: 10px 14px;
}

.secondary-button:disabled,
.text-button:disabled {
  cursor: not-allowed;
  opacity: 0.6;
}

.action-output {
  margin-top: 12px;
  padding: 12px;
  border-radius: 12px;
  background: #ffffff;
  border: 1px solid #dbeafe;
}

.action-output-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.action-output p {
  margin: 8px 0 0;
  color: #334155;
  line-height: 1.8;
  white-space: pre-wrap;
}

.interview-output {
  border-color: #bbf7d0;
  background: #f0fdf4;
}

.text-button {
  padding: 6px 10px;
  font-size: 12px;
}
</style>
