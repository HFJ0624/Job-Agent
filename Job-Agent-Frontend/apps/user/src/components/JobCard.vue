<template>
  <article class="job-card">
    <div class="job-main">
      <div>
        <h3>{{ job.title }}</h3>
        <p class="job-meta">{{ job.city }} · {{ job.district }} · {{ job.experience }} · {{ job.education }}</p>
      </div>
      <strong>{{ job.salary }}</strong>
    </div>

    <div class="tag-row">
      <span v-for="tag in job.tags" :key="tag">{{ tag }}</span>
    </div>

    <p class="job-highlight">{{ job.highlight }}</p>

    <footer class="job-footer">
      <div>
        <b>{{ job.company }}</b>
        <span>{{ job.companyStage }} · {{ job.companySize }}</span>
      </div>
      <div class="match-score">{{ job.matchScore }}% 匹配</div>
    </footer>
  </article>
</template>

<script setup lang="ts">
import type { JobItem } from "../data";

defineProps<{
  job: JobItem;
}>();
</script>
