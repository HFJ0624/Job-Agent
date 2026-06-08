<template>
  <div class="job-match-panel">
    <div class="match-header">
      <div>
        <h3>岗位匹配分析</h3>
        <p>选择一份简历，系统会根据岗位 JD 和简历内容计算匹配度。</p>
      </div>

      <button
        class="primary-button"
        type="button"
        :disabled="!selectedResumeId || matching"
        @click="handleMatch"
      >
        {{ matching ? "分析中..." : matchResult ? "重新分析" : "开始分析" }}
      </button>
    </div>

    <label class="resume-field">
      <span>选择简历</span>
      <select v-model="selectedResumeId">
        <option value="">请选择简历</option>
        <option
          v-for="resume in resumes"
          :key="resume.id"
          :value="String(resume.id)"
        >
          {{ resume.resumeName }}
          {{ resume.isDefault === 1 ? "（默认）" : "" }}
        </option>
      </select>
    </label>

    <p v-if="errorMessage" class="form-error">{{ errorMessage }}</p>

    <div v-if="matchResult" class="match-result">
      <div class="match-score-card">
        <div class="score-circle">{{ matchResult.matchScore }}</div>
        <div>
          <strong>{{ matchResult.matchLevel }}</strong>
          <span>{{ matchResult.recommendApply ? "建议投递" : "谨慎投递" }}</span>
          <p>分析时间：{{ matchResult.createTime || "-" }}</p>
        </div>
      </div>

      <div class="match-dimensions">
        <div>
          <span>技能匹配</span>
          <b>{{ matchResult.skillScore }}/100</b>
        </div>
        <div>
          <span>项目经验</span>
          <b>{{ matchResult.projectScore }}/100</b>
        </div>
        <div>
          <span>基础条件</span>
          <b>{{ matchResult.conditionScore }}/100</b>
        </div>
        <div>
          <span>求职偏好</span>
          <b>{{ matchResult.preferenceScore }}/100</b>
        </div>
      </div>

      <section class="analysis-card">
        <h4>已匹配技能</h4>
        <div class="tag-list">
          <span v-for="skill in matchResult.matchedSkills" :key="skill" class="skill-tag matched">
            {{ skill }}
          </span>
          <span v-if="!matchResult.matchedSkills.length" class="empty-text">暂无明显命中技能</span>
        </div>
      </section>

      <section class="analysis-card warning">
        <h4>缺失技能</h4>
        <div class="tag-list">
          <span v-for="skill in matchResult.missingSkills" :key="skill" class="skill-tag missing">
            {{ skill }}
          </span>
          <span v-if="!matchResult.missingSkills.length" class="empty-text">暂无明显缺失技能</span>
        </div>
      </section>

      <section class="analysis-card">
        <h4>优势</h4>
        <ul>
          <li v-for="item in matchResult.advantages" :key="item">{{ item }}</li>
        </ul>
      </section>

      <section class="analysis-card warning">
        <h4>风险点</h4>
        <ul>
          <li v-for="item in matchResult.riskPoints" :key="item">{{ item }}</li>
        </ul>
      </section>

      <section class="analysis-card suggestion">
        <h4>优化建议</h4>
        <ul>
          <li v-for="item in matchResult.suggestions" :key="item">{{ item }}</li>
        </ul>
      </section>
    </div>

    <p v-else class="empty-state">暂无匹配结果，请选择简历后点击开始分析。</p>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref, watch } from "vue";
import { ElMessage } from "element-plus";
import { listResumes } from "../api/resume";
import { getLatestJobMatch, matchJob } from "../api/job";
import type { JobMatchInfo, ResumeInfo } from "../api/types";

/**
 * 父组件传入岗位ID。
 */
const props = defineProps<{
  jobId: string;
}>();

const resumes = ref<ResumeInfo[]>([]);
const selectedResumeId = ref("");
const matchResult = ref<JobMatchInfo | null>(null);
const matching = ref(false);
const errorMessage = ref("");

onMounted(async () => {
  await loadResumes();
});

/**
 * 加载用户简历列表。
 * 说明：默认选择默认简历，如果没有默认简历，就选择第一份简历。
 */
async function loadResumes() {
  try {
    resumes.value = await listResumes();

    const defaultResume = resumes.value.find(item => item.isDefault === 1);
    const firstResume = resumes.value[0];

    selectedResumeId.value = String(defaultResume?.id || firstResume?.id || "");

    if (selectedResumeId.value) {
      await loadLatestMatch();
    }
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : "简历列表加载失败";
  }
}

/**
 * 加载最近一次匹配结果。
 */
async function loadLatestMatch() {
  if (!props.jobId || !selectedResumeId.value) {
    matchResult.value = null;
    return;
  }

  try {
    matchResult.value = await getLatestJobMatch(props.jobId, selectedResumeId.value);
  } catch (error) {
    console.error("[Job-Agent] 加载岗位匹配记录失败", error);
  }
}

/**
 * 执行岗位匹配分析。
 */
async function handleMatch() {
  if (!selectedResumeId.value) {
    ElMessage.warning("请先选择一份简历");
    return;
  }

  matching.value = true;
  errorMessage.value = "";

  try {
    matchResult.value = await matchJob(props.jobId, selectedResumeId.value);
    ElMessage.success("岗位匹配分析完成");
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : "岗位匹配分析失败";
    ElMessage.error(errorMessage.value);
  } finally {
    matching.value = false;
  }
}

/**
 * 用户切换简历时，自动查询该简历和当前岗位的最近一次匹配记录。
 */
watch(selectedResumeId, () => {
  loadLatestMatch();
});
</script>

<style scoped>
.job-match-panel {
  padding: 18px;
  border-radius: 16px;
  background: #ffffff;
  border: 1px solid #e5e7eb;
}

.match-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}

.match-header h3 {
  margin: 0;
  color: #111827;
}

.match-header p {
  margin: 6px 0 0;
  color: #6b7280;
  font-size: 13px;
}

.match-result {
  margin-top: 18px;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.match-score-card {
  display: flex;
  align-items: center;
  gap: 18px;
  padding: 18px;
  border-radius: 16px;
  background: linear-gradient(135deg, #eff6ff, #ffffff);
  border: 1px solid #bfdbfe;
}

.score-circle {
  width: 88px;
  height: 88px;
  border-radius: 50%;
  background: #2563eb;
  color: #ffffff;
  font-size: 28px;
  font-weight: 800;
  display: flex;
  align-items: center;
  justify-content: center;
}

.match-score-card strong {
  display: block;
  color: #111827;
  font-size: 22px;
}

.match-score-card span {
  display: inline-block;
  margin-top: 6px;
  color: #2563eb;
  font-weight: 600;
}

.match-score-card p {
  margin: 6px 0 0;
  color: #6b7280;
  font-size: 13px;
}

.match-dimensions {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.match-dimensions div {
  padding: 14px;
  border-radius: 12px;
  background: #f9fafb;
  border: 1px solid #e5e7eb;
  display: flex;
  justify-content: space-between;
}

.analysis-card {
  padding: 14px;
  border-radius: 14px;
  border: 1px solid #e5e7eb;
  background: #ffffff;
}

.analysis-card h4 {
  margin: 0 0 10px;
  color: #111827;
}

.analysis-card ul {
  margin: 0;
  padding-left: 18px;
}

.analysis-card li {
  line-height: 1.8;
  color: #374151;
}

.analysis-card.warning {
  border-color: #fed7aa;
  background: #fff7ed;
}

.analysis-card.suggestion {
  border-color: #bbf7d0;
  background: #f0fdf4;
}

.tag-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.skill-tag {
  padding: 4px 10px;
  border-radius: 999px;
  font-size: 13px;
}

.skill-tag.matched {
  background: #ecfdf5;
  color: #047857;
}

.skill-tag.missing {
  background: #fef3c7;
  color: #92400e;
}

.empty-text {
  color: #6b7280;
  font-size: 13px;
}
</style>