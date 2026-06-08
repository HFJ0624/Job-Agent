<template>
  <main class="page-section recommend-page">
    <section class="recommend-hero">
      <div>
        <p class="eyebrow">智能推荐</p>
        <h1>根据你的求职偏好推荐岗位</h1>
        <p>系统会结合期望岗位、城市、薪资、技能、学历和经验，为你推荐更合适的职位。</p>
      </div>

      <button class="primary-button large" type="button" :disabled="loading" @click="handleRecommend">
        {{ loading ? "推荐中..." : "刷新推荐" }}
      </button>
    </section>

    <section class="preference-card">
      <div class="section-heading">
        <div>
          <p class="eyebrow">求职偏好</p>
          <h2>完善偏好，提高推荐准确度</h2>
        </div>
        <button class="primary-button" type="button" :disabled="saving" @click="handleSavePreference">
          {{ saving ? "保存中..." : "保存偏好" }}
        </button>
      </div>

      <div class="preference-grid">
        <label>
          <span>期望岗位</span>
          <input v-model.trim="form.expectedJobTitle" placeholder="例如 Java 后端开发" />
        </label>

        <label>
          <span>期望城市</span>
          <input v-model.trim="form.expectedCity" placeholder="例如 上海" />
        </label>

        <label>
          <span>最低薪资</span>
          <input v-model.number="form.minSalary" type="number" placeholder="例如 15000" />
        </label>

        <label>
          <span>最高薪资</span>
          <input v-model.number="form.maxSalary" type="number" placeholder="例如 25000" />
        </label>

        <label>
          <span>学历</span>
          <input v-model.trim="form.expectedEducation" placeholder="例如 本科" />
        </label>

        <label>
          <span>经验</span>
          <input v-model.trim="form.expectedExperience" placeholder="例如 应届 / 1年 / 3年" />
        </label>

        <label>
          <span>工作类型</span>
          <input v-model.trim="form.expectedWorkType" placeholder="例如 全职 / 实习 / 远程" />
        </label>

        <label>
          <span>技能关键词</span>
          <input v-model.trim="form.skillKeywords" placeholder="例如 Java,Spring Boot,MySQL,Redis" />
        </label>
      </div>

      <label class="remark-field">
        <span>补充说明</span>
        <textarea v-model.trim="form.remark" placeholder="例如 更倾向后端开发、AI应用开发、业务系统方向。" />
      </label>
    </section>

    <section class="recommend-filter-card">
      <label>
        <span>临时关键词</span>
        <input v-model.trim="query.keyword" placeholder="可为空，例如 Java 后端" />
      </label>

      <label>
        <span>临时城市</span>
        <input v-model.trim="query.city" placeholder="可为空，例如 上海" />
      </label>

      <label>
        <span>推荐数量</span>
        <input v-model.number="query.limit" type="number" min="1" max="30" />
      </label>

      <button class="secondary-button" type="button" :disabled="loading" @click="handleRecommend">
        开始推荐
      </button>
    </section>

    <p v-if="errorMessage" class="form-error">{{ errorMessage }}</p>

    <section class="recommend-list">
      <p v-if="loading" class="empty-state">正在计算岗位推荐...</p>

      <p v-else-if="!recommendList.length" class="empty-state">
        暂无推荐结果，请先完善求职偏好或录入岗位数据。
      </p>

      <article v-for="job in recommendList" :key="job.jobId" class="recommend-item">
        <div class="recommend-main">
          <div class="recommend-title-row">
            <h3>{{ job.jobTitle }}</h3>
            <span class="score-pill">{{ job.recommendScore }} 分 · {{ job.recommendLevel }}</span>
          </div>

          <p class="company-line">
            {{ job.companyName || "未知公司" }} · {{ formatPlace(job.city, job.district) }}
          </p>

          <p class="salary-line">
            {{ formatSalary(job) }} · {{ job.educationReq || "学历不限" }} · {{ job.experienceReq || "经验不限" }}
          </p>

          <div class="skill-row">
            <span v-for="skill in splitTags(job.skillKeywords)" :key="skill">
              {{ skill }}
            </span>
          </div>

          <div class="reason-box">
            <b>推荐理由</b>
            <ul>
              <li v-for="reason in job.reasons" :key="reason">{{ reason }}</li>
            </ul>
          </div>
        </div>

        <div class="recommend-actions">
          <RouterLink class="primary-button" :to="`/jobs/${job.jobId}`">
            查看岗位
          </RouterLink>
        </div>
      </article>
    </section>
  </main>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import { RouterLink } from "vue-router";
import { ElMessage } from "element-plus";
import {
  getJobPreference,
  recommendJobs,
  saveJobPreference
} from "../api/jobPreference";
import type {
  JobRecommendInfo,
  UserJobPreferenceInfo
} from "../api/types";

/**
 * 求职偏好表单。
 */
const form = reactive<UserJobPreferenceInfo>({
  expectedJobTitle: "",
  expectedCity: "",
  minSalary: undefined,
  maxSalary: undefined,
  expectedEducation: "",
  expectedExperience: "",
  expectedWorkType: "",
  skillKeywords: "",
  remark: ""
});

/**
 * 推荐查询条件。
 */
const query = reactive({
  keyword: "",
  city: "",
  limit: 10
});

const recommendList = ref<JobRecommendInfo[]>([]);
const loading = ref(false);
const saving = ref(false);
const errorMessage = ref("");

onMounted(async () => {
  await loadPreference();
  await handleRecommend();
});

/**
 * 加载用户求职偏好。
 */
async function loadPreference() {
  try {
    const preference = await getJobPreference();

    if (preference) {
      Object.assign(form, preference);
    }
  } catch (error) {
    console.error("[Job-Agent] 加载求职偏好失败", error);
  }
}

/**
 * 保存求职偏好。
 */
async function handleSavePreference() {
  saving.value = true;
  errorMessage.value = "";

  try {
    const saved = await saveJobPreference(form);
    Object.assign(form, saved);
    ElMessage.success("求职偏好已保存");
    await handleRecommend();
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : "求职偏好保存失败";
    ElMessage.error(errorMessage.value);
  } finally {
    saving.value = false;
  }
}

/**
 * 执行岗位推荐。
 */
async function handleRecommend() {
  loading.value = true;
  errorMessage.value = "";

  try {
    recommendList.value = await recommendJobs({
      keyword: query.keyword,
      city: query.city,
      limit: query.limit
    });
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : "岗位推荐失败";
    ElMessage.error(errorMessage.value);
  } finally {
    loading.value = false;
  }
}

/**
 * 格式化地点。
 */
function formatPlace(city?: string, district?: string) {
  return [city, district].filter(Boolean).join(" · ") || "地点待补充";
}

/**
 * 格式化薪资。
 */
function formatSalary(job: JobRecommendInfo) {
  if (!job.minSalary && !job.maxSalary) {
    return "薪资面议";
  }

  const min = job.minSalary ? Math.round(job.minSalary / 1000) : 0;
  const max = job.maxSalary ? Math.round(job.maxSalary / 1000) : 0;

  if (min && max) {
    return `${min}-${max}K`;
  }

  if (min) {
    return `${min}K起`;
  }

  return `${max}K以内`;
}

/**
 * 拆分技能标签。
 */
function splitTags(value?: string) {
  if (!value) {
    return [];
  }

  return value
    .split(/[,，、/\s]+/)
    .map(item => item.trim())
    .filter(Boolean)
    .slice(0, 8);
}
</script>

<style scoped>
.recommend-page {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.recommend-hero,
.preference-card,
.recommend-filter-card {
  padding: 24px;
  border-radius: 22px;
  background: #ffffff;
  border: 1px solid #e5e7eb;
}

.recommend-hero {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: linear-gradient(135deg, #eff6ff, #ffffff);
}

.recommend-hero h1 {
  margin: 6px 0;
  color: #111827;
}

.recommend-hero p {
  color: #6b7280;
}

.preference-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
}

.preference-grid label,
.remark-field,
.recommend-filter-card label {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.preference-grid span,
.remark-field span,
.recommend-filter-card span {
  color: #374151;
  font-size: 13px;
  font-weight: 600;
}

.preference-grid input,
.remark-field textarea,
.recommend-filter-card input {
  padding: 10px 12px;
  border-radius: 12px;
  border: 1px solid #d1d5db;
  font: inherit;
}

.remark-field {
  margin-top: 14px;
}

.remark-field textarea {
  min-height: 76px;
  resize: vertical;
}

.recommend-filter-card {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr)) auto;
  align-items: end;
  gap: 14px;
}

.recommend-list {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.recommend-item {
  display: flex;
  justify-content: space-between;
  gap: 18px;
  padding: 20px;
  border-radius: 18px;
  background: #ffffff;
  border: 1px solid #e5e7eb;
}

.recommend-main {
  flex: 1;
}

.recommend-title-row {
  display: flex;
  align-items: center;
  gap: 12px;
}

.recommend-title-row h3 {
  margin: 0;
  color: #111827;
}

.score-pill {
  padding: 4px 10px;
  border-radius: 999px;
  background: #ecfdf5;
  color: #047857;
  font-size: 13px;
  font-weight: 700;
}

.company-line,
.salary-line {
  margin: 8px 0;
  color: #6b7280;
}

.skill-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin: 12px 0;
}

.skill-row span {
  padding: 4px 9px;
  border-radius: 999px;
  background: #eff6ff;
  color: #2563eb;
  font-size: 12px;
}

.reason-box {
  padding: 12px;
  border-radius: 14px;
  background: #f9fafb;
}

.reason-box b {
  color: #111827;
}

.reason-box ul {
  margin: 8px 0 0;
  padding-left: 18px;
}

.reason-box li {
  color: #374151;
  line-height: 1.8;
}

.recommend-actions {
  display: flex;
  align-items: flex-start;
}

@media (max-width: 1000px) {
  .preference-grid,
  .recommend-filter-card {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .recommend-item {
    flex-direction: column;
  }
}

@media (max-width: 640px) {
  .preference-grid,
  .recommend-filter-card {
    grid-template-columns: 1fr;
  }

  .recommend-hero {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>