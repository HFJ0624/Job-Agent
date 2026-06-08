<template>
  <div class="resume-score-panel">
    <div class="score-header">
      <div>
        <h3>AI 简历评分</h3>
        <p>从基础信息、教育背景、技能栈、项目经历、工作经历和表达质量进行综合评分。</p>
      </div>

      <el-button
        type="primary"
        :loading="scoring"
        :disabled="!resume"
        @click="handleScore"
      >
        {{ scoreResult ? "重新评分" : "开始评分" }}
      </el-button>
    </div>

    <el-form label-width="90px" class="score-form">
      <el-form-item label="目标岗位">
        <el-input
          v-model="targetPosition"
          placeholder="例如：Java 后端开发，可不填"
          clearable
        />
      </el-form-item>
    </el-form>

    <el-empty
      v-if="!scoreResult"
      description="暂无评分结果，请点击开始评分"
    />

    <div v-else class="score-result">
      <div class="total-score-card">
        <div class="score-number">{{ scoreResult.totalScore }}</div>
        <div class="score-meta">
          <div class="score-level">{{ scoreResult.level }}</div>
          <div class="score-time">评分时间：{{ scoreResult.createTime || "-" }}</div>
        </div>
      </div>

      <el-row :gutter="12" class="dimension-row">
        <el-col :span="8">
          <div class="dimension-card">
            <span>基础信息</span>
            <strong>{{ scoreResult.basicInfoScore }}/10</strong>
          </div>
        </el-col>
        <el-col :span="8">
          <div class="dimension-card">
            <span>教育背景</span>
            <strong>{{ scoreResult.educationScore }}/10</strong>
          </div>
        </el-col>
        <el-col :span="8">
          <div class="dimension-card">
            <span>技能栈</span>
            <strong>{{ scoreResult.skillScore }}/20</strong>
          </div>
        </el-col>
        <el-col :span="8">
          <div class="dimension-card">
            <span>项目经历</span>
            <strong>{{ scoreResult.projectScore }}/35</strong>
          </div>
        </el-col>
        <el-col :span="8">
          <div class="dimension-card">
            <span>工作经历</span>
            <strong>{{ scoreResult.experienceScore }}/15</strong>
          </div>
        </el-col>
        <el-col :span="8">
          <div class="dimension-card">
            <span>表达质量</span>
            <strong>{{ scoreResult.expressionScore }}/10</strong>
          </div>
        </el-col>
      </el-row>

      <el-card class="analysis-card" shadow="never">
        <template #header>简历优势</template>
        <ul>
          <li v-for="item in scoreResult.advantages" :key="item">{{ item }}</li>
        </ul>
      </el-card>

      <el-card class="analysis-card" shadow="never">
        <template #header>存在问题</template>
        <ul>
          <li v-for="item in scoreResult.problems" :key="item">{{ item }}</li>
        </ul>
      </el-card>

      <el-card class="analysis-card" shadow="never">
        <template #header>优化建议</template>
        <ul>
          <li v-for="item in scoreResult.suggestions" :key="item">{{ item }}</li>
        </ul>
      </el-card>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref, watch } from "vue";
import { ElMessage } from "element-plus";
import { getLatestResumeScore, scoreResume } from "../api/resume";
import type { ResumeInfo, ResumeScoreInfo } from "../api/types";

/**
 * 组件参数。
 * P表示参数描述：父组件传入当前选中的简历。
 */
const props = defineProps<{
  resume: ResumeInfo | null;
}>();

/**
 * 评分完成后通知父组件刷新简历列表。
 */
const emit = defineEmits<{
  scored: [];
}>();

const targetPosition = ref("");
const scoring = ref(false);
const scoreResult = ref<ResumeScoreInfo | null>(null);

/**
 * 加载最近一次评分结果。
 */
async function loadLatestScore() {
  if (!props.resume?.id) {
    scoreResult.value = null;
    return;
  }

  try {
    scoreResult.value = await getLatestResumeScore(String(props.resume.id));
  } catch (error) {
    console.error("[Job-Agent] 查询简历评分失败", error);
  }
}

/**
 * 执行评分。
 */
async function handleScore() {
  if (!props.resume?.id) {
    ElMessage.warning("请先选择一份简历");
    return;
  }

  scoring.value = true;

  try {
    scoreResult.value = await scoreResume(String(props.resume.id), targetPosition.value);
    ElMessage.success("简历评分完成");
    emit("scored");
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : "简历评分失败");
  } finally {
    scoring.value = false;
  }
}

onMounted(loadLatestScore);

/**
 * 当用户切换简历时，自动加载对应简历的最近一次评分。
 */
watch(
  () => props.resume?.id,
  () => {
    loadLatestScore();
  }
);
</script>

<style scoped>
.resume-score-panel {
  padding: 16px;
  border: 1px solid #e5e7eb;
  border-radius: 14px;
  background: #ffffff;
}

.score-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}

.score-header h3 {
  margin: 0;
  font-size: 18px;
  color: #111827;
}

.score-header p {
  margin: 6px 0 0;
  font-size: 13px;
  color: #6b7280;
}

.score-form {
  max-width: 520px;
  margin-bottom: 16px;
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
  padding: 18px;
  border-radius: 14px;
  background: linear-gradient(135deg, #f0f7ff, #ffffff);
  border: 1px solid #dbeafe;
}

.score-number {
  width: 86px;
  height: 86px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #2563eb;
  color: #ffffff;
  font-size: 28px;
  font-weight: 700;
}

.score-level {
  font-size: 20px;
  font-weight: 700;
  color: #111827;
}

.score-time {
  margin-top: 6px;
  color: #6b7280;
  font-size: 13px;
}

.dimension-row {
  row-gap: 12px;
}

.dimension-card {
  padding: 14px;
  border-radius: 12px;
  background: #f9fafb;
  border: 1px solid #e5e7eb;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.dimension-card span {
  color: #6b7280;
}

.dimension-card strong {
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
</style>