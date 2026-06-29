<template>
  <main class="page learning-plan-page">
    <section class="learning-header">
      <div>
        <p class="eyebrow">Study Plan</p>
        <h1>AI 面试学习计划</h1>
        <p>基于错题本和 RAG 学习材料生成训练计划，并通过复测验证是否真的掌握。</p>
      </div>
      <div class="header-actions">
        <el-input-number v-model="planDays" :min="3" :max="14" />
        <el-button type="primary" :loading="generating" @click="generatePlan">生成计划</el-button>
      </div>
    </section>

    <el-empty v-if="!loading && !plan" description="暂无学习计划，请先完成 AI 面试并沉淀错题。" />

    <section v-if="plan" class="plan-summary table-card">
      <div>
        <h2>{{ plan.planTitle }}</h2>
        <p>{{ plan.planDays }} 天计划 · {{ plan.status === "FINISHED" ? "已完成" : "进行中" }}</p>
      </div>
      <div class="tag-list">
        <el-tag v-for="point in plan.weakKnowledgePoints" :key="point" type="warning">
          {{ point }}
        </el-tag>
      </div>
    </section>

    <section v-if="plan" class="day-list">
      <article v-for="item in plan.items" :key="item.id" class="day-card">
        <div class="day-head">
          <div>
            <span class="day-no">Day {{ item.dayNo }}</span>
            <h2>{{ item.title }}</h2>
          </div>
          <el-tag :type="item.completionStatus === 'DONE' ? 'success' : 'info'">
            {{ item.completionStatus === "DONE" ? "已完成" : "待完成" }}
          </el-tag>
        </div>

        <div class="task-grid">
          <section>
            <h3>知识点</h3>
            <p>{{ item.knowledgePoint }}</p>
          </section>
          <section>
            <h3>学习目标</h3>
            <p>{{ item.learningGoal || "-" }}</p>
          </section>
          <section>
            <h3>练习任务</h3>
            <p>{{ item.practiceTask || "-" }}</p>
          </section>
          <section>
            <h3>复习建议</h3>
            <p>{{ item.reviewSuggestion || "-" }}</p>
          </section>
        </div>

        <div class="materials" v-if="item.materials?.length">
          <h3>RAG 学习材料</h3>
          <section v-for="material in item.materials" :key="`${material.documentId}-${material.chunkId}`" class="material-card">
            <strong>{{ material.title || "学习材料" }}</strong>
            <p>{{ material.content }}</p>
            <small v-if="material.score !== undefined">相关度 {{ material.score }}</small>
          </section>
        </div>

        <div class="day-actions">
          <el-button
            v-if="item.completionStatus !== 'DONE'"
            type="success"
            @click="changeItemStatus(item.id, 'DONE')"
          >
            标记完成
          </el-button>
          <el-button v-else @click="changeItemStatus(item.id, 'PENDING')">改为待完成</el-button>
          <el-button type="primary" plain :loading="retestLoading" @click="startRetest(item.id)">
            立即复测
          </el-button>
        </div>
      </article>
    </section>

    <el-dialog v-model="retestDialogVisible" title="学习任务复测" width="720px">
      <section v-if="currentRetest" class="retest-dialog">
        <el-tag>{{ currentRetest.knowledgePoint }}</el-tag>
        <h3>{{ currentRetest.questionContent }}</h3>
        <el-input
          v-model="retestAnswer"
          type="textarea"
          :rows="7"
          placeholder="请输入你的复测回答，建议包含核心概念、常见追问和项目表达。"
        />

        <div v-if="currentRetest.status === 'SUBMITTED'" class="retest-result">
          <el-tag :type="currentRetest.passed ? 'success' : 'danger'">
            {{ currentRetest.passed ? "复测通过" : "继续复习" }}
          </el-tag>
          <strong>{{ currentRetest.score ?? "-" }} 分</strong>
          <p>{{ currentRetest.feedback }}</p>
          <p>{{ currentRetest.suggestion }}</p>
        </div>
      </section>
      <template #footer>
        <el-button @click="retestDialogVisible = false">关闭</el-button>
        <el-button
          type="primary"
          :disabled="!currentRetest || currentRetest.status === 'SUBMITTED'"
          :loading="retestSubmitting"
          @click="submitRetest"
        >
          提交复测
        </el-button>
      </template>
    </el-dialog>
  </main>
</template>

<script setup lang="ts">
import { onMounted, ref } from "vue";
import { ElMessage } from "element-plus";
import {
  generateMockInterviewLearningPlan,
  getLatestMockInterviewLearningPlan,
  startMockInterviewLearningPlanRetest,
  submitMockInterviewLearningPlanRetest,
  updateMockInterviewLearningPlanItemStatus
} from "../api/mockInterviewLearningPlan";
import type { MockInterviewLearningPlanInfo, MockInterviewStudyPlanRetestInfo } from "../api/types";

const loading = ref(false);
const generating = ref(false);
const retestLoading = ref(false);
const retestSubmitting = ref(false);
const retestDialogVisible = ref(false);
const retestAnswer = ref("");
const currentRetest = ref<MockInterviewStudyPlanRetestInfo | null>(null);
const planDays = ref(7);
const plan = ref<MockInterviewLearningPlanInfo | null>(null);

onMounted(loadLatestPlan);

async function loadLatestPlan() {
  loading.value = true;
  try {
    plan.value = await getLatestMockInterviewLearningPlan();
  } finally {
    loading.value = false;
  }
}

async function generatePlan() {
  generating.value = true;
  try {
    plan.value = await generateMockInterviewLearningPlan(planDays.value);
    ElMessage.success("学习计划已生成");
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : "学习计划生成失败");
  } finally {
    generating.value = false;
  }
}

async function changeItemStatus(itemId: number, completionStatus: string) {
  plan.value = await updateMockInterviewLearningPlanItemStatus(itemId, completionStatus);
  ElMessage.success("学习任务状态已更新");
}

async function startRetest(itemId: number) {
  retestLoading.value = true;
  try {
    currentRetest.value = await startMockInterviewLearningPlanRetest(itemId);
    retestAnswer.value = "";
    retestDialogVisible.value = true;
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : "复测创建失败");
  } finally {
    retestLoading.value = false;
  }
}

async function submitRetest() {
  if (!currentRetest.value) return;
  if (!retestAnswer.value.trim()) {
    ElMessage.warning("请先填写复测回答");
    return;
  }

  retestSubmitting.value = true;
  try {
    currentRetest.value = await submitMockInterviewLearningPlanRetest(currentRetest.value.id, retestAnswer.value);
    ElMessage.success(currentRetest.value.passed ? "复测通过，相关错题已标记掌握" : "复测未通过，相关错题保持复习中");
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : "复测提交失败");
  } finally {
    retestSubmitting.value = false;
  }
}
</script>

<style scoped>
.learning-plan-page {
  max-width: 1180px;
  margin: 0 auto;
  padding: 32px 20px 40px;
}

.learning-header,
.header-actions,
.plan-summary,
.day-head,
.day-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
}

.learning-header {
  margin-bottom: 18px;
}

.learning-header h1,
.plan-summary h2,
.day-card h2 {
  margin: 4px 0 8px;
  color: #0f172a;
}

.learning-header p,
.plan-summary p {
  margin: 0;
  color: #64748b;
}

.plan-summary {
  margin-bottom: 16px;
}

.tag-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.day-list {
  display: grid;
  gap: 14px;
}

.day-card {
  padding: 16px;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  background: #ffffff;
}

.day-no {
  color: #0f766e;
  font-weight: 700;
}

.task-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12px;
}

.task-grid section,
.material-card {
  padding: 12px;
  border-radius: 8px;
  background: #f8fafc;
}

.task-grid h3,
.materials h3 {
  margin: 0 0 8px;
  color: #334155;
  font-size: 14px;
}

.task-grid p,
.material-card p,
.retest-dialog p {
  margin: 0;
  color: #475569;
  line-height: 1.7;
}

.materials {
  display: grid;
  gap: 10px;
  margin-top: 12px;
}

.material-card strong {
  display: block;
  margin-bottom: 6px;
  color: #0f766e;
}

.material-card small {
  display: block;
  margin-top: 6px;
  color: #64748b;
}

.day-actions {
  justify-content: flex-end;
  margin-top: 14px;
}

.retest-dialog {
  display: grid;
  gap: 14px;
}

.retest-dialog h3 {
  margin: 0;
  color: #0f172a;
  line-height: 1.6;
}

.retest-result {
  display: grid;
  gap: 8px;
  padding: 12px;
  border-radius: 8px;
  background: #f8fafc;
}

@media (max-width: 760px) {
  .learning-header,
  .header-actions,
  .plan-summary,
  .day-head {
    align-items: flex-start;
    flex-direction: column;
  }

  .task-grid {
    grid-template-columns: 1fr;
  }
}
</style>
