<script setup lang="ts">
import JobCard from "../components/JobCard.vue";
import { jobs } from "../data";

const filters = ["全部", "上海", "杭州", "深圳", "北京"];
</script>

<template>
  <main class="page-section">
    <div class="list-toolbar">
      <div>
        <p class="eyebrow">职位搜索</p>
        <h1>根据偏好筛选岗位</h1>
      </div>
      <div class="search-panel compact">
        <input aria-label="职位关键词" placeholder="Java 后端 / AI 应用 / RAG" />
        <button>搜索</button>
      </div>
    </div>

    <div class="filter-row">
      <button v-for="filter in filters" :key="filter" :class="{ active: filter === '全部' }">{{ filter }}</button>
    </div>

    <div class="jobs-layout">
      <aside class="filter-panel">
        <h3>筛选条件</h3>
        <label><input type="checkbox" checked /> 薪资 15K 以上</label>
        <label><input type="checkbox" /> 双休</label>
        <label><input type="checkbox" /> 离家近</label>
        <label><input type="checkbox" /> 发展前景好</label>
      </aside>

      <section class="job-list">
        <JobCard v-for="job in jobs" :key="job.id" :job="job" />
      </section>
    </div>
  </main>
</template>
