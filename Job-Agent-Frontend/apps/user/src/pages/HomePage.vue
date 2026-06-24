<template>
  <main>
    <section class="hero-section">
      <div class="hero-copy">
        <p class="eyebrow">智能求职助手</p>
        <h1>让 AI 帮你找更匹配的工作</h1>
        <p class="hero-subtitle">上传简历后，系统会结合岗位 JD、薪资、地点和技能栈，给出匹配度、差距分析和 HR 打招呼语。</p>

        <div class="search-panel">
          <input
            v-model.trim="keyword"
            aria-label="职位关键词"
            placeholder="搜索职位、公司或技能，例如 Java / AI 应用"
            @keyup.enter="searchJobs"
          />
          <button type="button" @click="searchJobs">搜索职位</button>
        </div>

        <div class="hot-filter-row">
          <span v-for="filter in hotFilters" :key="filter">{{ filter }}</span>
        </div>
      </div>

      <aside class="hero-card">
        <div class="ai-badge">AI Match</div>
        <h2>简历匹配报告</h2>
        <p>{{ resumeReportText }}</p>
        <div class="score-ring">
          <span>{{ resumeScoreText }}</span>
          <small>{{ resumeScoreLabel }}</small>
        </div>
      </aside>
    </section>

    <section class="content-grid">
      <div class="main-column">
        <div class="section-heading">
          <div>
            <p class="eyebrow">推荐职位</p>
            <h2>更适合你的岗位</h2>
          </div>
          <RouterLink to="/jobs">查看全部</RouterLink>
        </div>

        <p v-if="loading" class="empty-state">正在加载首页数据...</p>
        <p v-else-if="errorMessage" class="form-error">{{ errorMessage }}</p>
        <p v-else-if="!jobs.length" class="empty-state">暂无已发布岗位，后台发布后会展示在这里。</p>
        <template v-else>
          <JobCard v-for="job in jobs" :key="job.id" :job="job" />
        </template>
      </div>

      <aside class="side-column">
        <section class="side-panel">
          <h3>热门公司</h3>
          <p v-if="loading" class="empty-state">正在加载热门公司...</p>
          <p v-else-if="!companies.length" class="empty-state">暂无热门公司，发布岗位后会自动统计。</p>
          <template v-else>
            <div v-for="company in companies" :key="company.id" class="company-mini-card">
              <div>
                <b>{{ company.companyName }}</b>
                <span>{{ formatCompanyMeta(company) }}</span>
              </div>
              <strong>{{ company.jobCount }} 个岗位</strong>
            </div>
          </template>
        </section>

        <section class="side-panel assistant-panel">
          <h3>AI 求职建议</h3>
          <p>{{ aiSuggestion }}</p>
          <RouterLink to="/agent">继续问 AI</RouterLink>
        </section>
      </aside>
    </section>
  </main>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import { getHomeOverview } from "../api/home";
import type { HomeHotCompanyInfo, HomeResumeMatchReportInfo, PositionInfo } from "../api/types";
import JobCard from "../components/JobCard.vue";

const hotFilters = ["Java 后端", "AI 应用", "上海", "15K 以上", "双休", "离家近"];
const router = useRouter();
const keyword = ref("");
const jobs = ref<PositionInfo[]>([]);
const companies = ref<HomeHotCompanyInfo[]>([]);
const resumeReport = ref<HomeResumeMatchReportInfo | null>(null);
const aiSuggestion = ref("正在根据你的简历和岗位数据生成建议...");
const loading = ref(false);
const errorMessage = ref("");

const resumeReportText = computed(() => {
  const report = resumeReport.value;
  if (!report?.hasResume) {
    return "你还没有上传简历，上传后首页会展示真实评分和优化建议。";
  }
  if (!report.hasScore) {
    return `${report.resumeName || "默认简历"} 已上传，完成 AI 评分后会展示真实报告。`;
  }
  return report.summary || `${report.resumeName || "默认简历"} 已完成评分，可以结合推荐岗位继续做匹配分析。`;
});

const resumeScoreText = computed(() => {
  const score = resumeReport.value?.score;
  return score == null ? "--" : `${score}`;
});

const resumeScoreLabel = computed(() => {
  const report = resumeReport.value;
  if (!report?.hasResume) {
    return "未上传";
  }
  if (!report.hasScore) {
    return "未评分";
  }
  return report.level || "简历评分";
});

async function loadHomeOverview() {
  loading.value = true;
  errorMessage.value = "";
  try {
    // 1. 首页只调用一个聚合接口，避免推荐岗位、热门公司、简历报告分别加载导致状态不一致。
    const overview = await getHomeOverview();
    jobs.value = overview.recommendedJobs || [];
    companies.value = overview.hotCompanies || [];
    resumeReport.value = overview.resumeMatchReport || null;
    aiSuggestion.value = overview.aiSuggestion || "暂无建议，请先上传简历或等待后台发布岗位。";
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : "首页数据加载失败";
    jobs.value = [];
    companies.value = [];
    resumeReport.value = null;
    aiSuggestion.value = "首页数据加载失败，请稍后刷新重试。";
  } finally {
    loading.value = false;
  }
}

function searchJobs() {
  // 1. 首页搜索只负责带关键词跳转，岗位列表页会读取 query.keyword 并执行真实筛选。
  router.push({
    path: "/jobs",
    query: keyword.value ? { keyword: keyword.value } : {}
  });
}

function formatCompanyMeta(company: HomeHotCompanyInfo) {
  return [company.industry, company.financingStage, company.companySize].filter(Boolean).join(" · ") || "公司信息待补充";
}

onMounted(loadHomeOverview);
</script>
