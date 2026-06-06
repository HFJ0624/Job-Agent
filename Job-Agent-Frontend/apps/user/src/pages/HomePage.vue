<template>
  <main>
    <section class="hero-section">
      <div class="hero-copy">
        <p class="eyebrow">智能求职助手</p>
        <h1>让 AI 帮你找更匹配的工作</h1>
        <p class="hero-subtitle">上传简历后，系统会结合岗位 JD、薪资、地点和技能栈，给出匹配度、差距分析和 HR 打招呼语。</p>

        <div class="search-panel">
          <input aria-label="职位关键词" placeholder="搜索职位、公司或技能，例如 Java / AI 应用" />
          <button>搜索职位</button>
        </div>

        <div class="hot-filter-row">
          <span v-for="filter in hotFilters" :key="filter">{{ filter }}</span>
        </div>
      </div>

      <aside class="hero-card">
        <div class="ai-badge">AI Match</div>
        <h2>简历匹配报告</h2>
        <p>默认简历已识别 18 项技能，推荐优先投递后端与 AI 应用方向。</p>
        <div class="score-ring">
          <span>92%</span>
          <small>最高匹配</small>
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

        <p v-if="loadingJobs" class="empty-state">正在加载推荐岗位...</p>
        <p v-else-if="!jobs.length" class="empty-state">暂无已发布岗位，后台发布后会展示在这里。</p>
        <template v-else>
          <JobCard v-for="job in jobs" :key="job.id" :job="job" />
        </template>
      </div>

      <aside class="side-column">
        <section class="side-panel">
          <h3>热门公司</h3>
          <div v-for="company in companies" :key="company.id" class="company-mini-card">
            <div>
              <b>{{ company.name }}</b>
              <span>{{ company.industry }} · {{ company.stage }}</span>
            </div>
            <strong>{{ company.jobs }} 个岗位</strong>
          </div>
        </section>

        <section class="side-panel assistant-panel">
          <h3>AI 求职建议</h3>
          <p>你的简历中 Spring Boot 和 Redis 经验比较突出，可以在项目里补充性能指标，会更容易打动招聘方。</p>
          <RouterLink to="/agent">继续问 AI</RouterLink>
        </section>
      </aside>
    </section>
  </main>
</template>

<script setup lang="ts">
import { onMounted, ref } from "vue";
import { pageFrontPositions } from "../api/job";
import type { PositionInfo } from "../api/types";
import JobCard from "../components/JobCard.vue";
import { companies } from "../data";

const hotFilters = ["Java 后端", "AI 应用", "上海", "15K 以上", "双休", "离家近"];
const jobs = ref<PositionInfo[]>([]);
const loadingJobs = ref(false);

async function loadRecommendedJobs() {
  loadingJobs.value = true;
  try {
    // 1. 首页只取前 3 条已发布岗位，完整列表交给 /jobs 页面展示。
    const page = await pageFrontPositions({ pageNo: 1, pageSize: 3 });
    jobs.value = page.records;
  } catch (error) {
    console.error("[Job-Agent] 首页推荐岗位加载失败", error);
    jobs.value = [];
  } finally {
    loadingJobs.value = false;
  }
}

onMounted(loadRecommendedJobs);
</script>
