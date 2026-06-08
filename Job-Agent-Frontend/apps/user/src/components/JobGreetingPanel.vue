<template>
  <section class="greeting-panel">
    <div class="greeting-header">
      <div>
        <p class="eyebrow">AI 话术</p>
        <h3>生成 HR 打招呼语</h3>
      </div>

      <button
        class="secondary-button"
        type="button"
        :disabled="generating || !selectedResumeId"
        @click="handleGenerate"
      >
        {{ generating ? "生成中..." : "生成" }}
      </button>
    </div>

    <label class="message-field">
      <span>选择简历</span>
      <select v-model="selectedResumeId">
        <option value="">请选择简历</option>
        <option
          v-for="resume in resumes"
          :key="resume.id"
          :value="String(resume.id)"
        >
          {{ resume.resumeName }}{{ resume.isDefault === 1 ? "（默认）" : "" }}
        </option>
      </select>
    </label>

    <label class="message-field">
      <span>语气风格</span>
      <select v-model="style">
        <option value="自然">自然</option>
        <option value="正式">正式</option>
        <option value="自信">自信</option>
        <option value="实习生风格">实习生风格</option>
        <option value="社招风格">社招风格</option>
        <option value="简洁直达">简洁直达</option>
      </select>
    </label>

    <p v-if="errorMessage" class="form-error">{{ errorMessage }}</p>

    <div v-if="greeting" class="greeting-result">
      <div class="greeting-result-top">
        <b>生成结果</b>
        <span>{{ greeting.style }} · {{ greeting.source }}</span>
      </div>

      <p>{{ greeting.content }}</p>

      <div v-if="greeting.matchedSkills?.length" class="greeting-skills">
        <span
          v-for="skill in greeting.matchedSkills"
          :key="skill"
        >
          {{ skill }}
        </span>
      </div>

      <div class="greeting-actions">
        <button class="primary-button" type="button" @click="emitUseGreeting">
          使用这段话
        </button>
        <button class="secondary-button" type="button" @click="copyGreeting">
          复制
        </button>
      </div>
    </div>

    <p v-else class="greeting-tip">
      建议先完成岗位匹配分析，系统会优先使用已匹配技能生成更贴合岗位的话术。
    </p>
  </section>
</template>

<script setup lang="ts">
import { onMounted, ref } from "vue";
import { ElMessage } from "element-plus";
import { listResumes } from "../api/resume";
import { generateGreeting } from "../api/job";
import type { GreetingInfo, ResumeInfo } from "../api/types";

/**
 * 父组件传入当前岗位ID。
 */
const props = defineProps<{
  jobId: string;
}>();

/**
 * 生成结果给父组件使用。
 * 例如：父组件把这段话填入“沟通消息”textarea。
 */
const emit = defineEmits<{
  use: [content: string];
}>();

const resumes = ref<ResumeInfo[]>([]);
const selectedResumeId = ref("");
const style = ref("自然");
const greeting = ref<GreetingInfo | null>(null);
const generating = ref(false);
const errorMessage = ref("");

onMounted(async () => {
  await loadResumes();
});

/**
 * 加载当前用户的简历列表。
 * 默认选中默认简历；如果没有默认简历，就选第一份。
 */
async function loadResumes() {
  try {
    resumes.value = await listResumes();

    const defaultResume = resumes.value.find(item => item.isDefault === 1);
    const firstResume = resumes.value[0];

    selectedResumeId.value = String(defaultResume?.id || firstResume?.id || "");
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : "简历列表加载失败";
  }
}

/**
 * 调用后端生成打招呼语。
 */
async function handleGenerate() {
  if (!selectedResumeId.value) {
    ElMessage.warning("请先选择一份简历");
    return;
  }

  generating.value = true;
  errorMessage.value = "";

  try {
    greeting.value = await generateGreeting(
      props.jobId,
      selectedResumeId.value,
      style.value
    );

    ElMessage.success("打招呼语已生成");
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : "打招呼语生成失败";
    ElMessage.error(errorMessage.value);
  } finally {
    generating.value = false;
  }
}

/**
 * 把生成结果传给父组件。
 */
function emitUseGreeting() {
  if (!greeting.value?.content) {
    return;
  }

  emit("use", greeting.value.content);
  ElMessage.success("已填入沟通消息");
}

/**
 * 复制生成结果。
 */
async function copyGreeting() {
  if (!greeting.value?.content) {
    return;
  }

  try {
    await navigator.clipboard.writeText(greeting.value.content);
    ElMessage.success("已复制到剪贴板");
  } catch (error) {
    ElMessage.error("复制失败，请手动复制");
  }
}
</script>

<style scoped>
.greeting-panel {
  margin-top: 16px;
  padding: 14px;
  border-radius: 16px;
  background: #f8fafc;
  border: 1px solid #e5e7eb;
}

.greeting-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.greeting-header h3 {
  margin: 4px 0 0;
  color: #111827;
  font-size: 16px;
}

.greeting-result {
  margin-top: 12px;
  padding: 12px;
  border-radius: 14px;
  background: #ffffff;
  border: 1px solid #dbeafe;
}

.greeting-result-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 8px;
}

.greeting-result-top b {
  color: #111827;
}

.greeting-result-top span {
  color: #6b7280;
  font-size: 12px;
}

.greeting-result p {
  margin: 0;
  color: #374151;
  line-height: 1.7;
}

.greeting-skills {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 10px;
}

.greeting-skills span {
  padding: 3px 8px;
  border-radius: 999px;
  background: #ecfdf5;
  color: #047857;
  font-size: 12px;
}

.greeting-actions {
  display: flex;
  gap: 10px;
  margin-top: 12px;
}

.greeting-tip {
  margin: 10px 0 0;
  color: #6b7280;
  font-size: 13px;
  line-height: 1.6;
}
</style>