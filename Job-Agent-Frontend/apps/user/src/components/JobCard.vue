<template>
  <RouterLink class="job-card job-card-link" :to="`/jobs/${job.id}`">
    <div class="job-main">
      <div>
        <h3>{{ job.jobTitle }}</h3>
        <p class="job-meta">{{ formatMeta(job) }}</p>
      </div>
      <strong>{{ formatSalary(job) }}</strong>
    </div>

    <div class="tag-row">
      <span v-for="tag in visibleTags" :key="tag">{{ tag }}</span>
    </div>

    <p class="job-highlight">{{ formatHighlight(job) }}</p>

    <footer class="job-footer">
      <div>
        <b>{{ job.companyName || "未命名公司" }}</b>
        <span>{{ formatCompanyMeta(job) }}</span>
      </div>
      <div class="match-score">{{ estimateMatchScore(job.id) }}% 匹配</div>
    </footer>
  </RouterLink>
</template>

<script setup lang="ts">
import { computed } from "vue";
import type { PositionInfo } from "../api/types";

const props = defineProps<{
  job: PositionInfo;
}>();

const visibleTags = computed(() => {
  // 1. 技能和福利在数据库里用逗号保存，展示时拆成标签更像招聘网站列表。
  return [
    props.job.jobCategory,
    props.job.workType,
    ...splitTags(props.job.skillKeywords),
    ...splitTags(props.job.welfareTags)
  ]
    .filter(Boolean)
    .slice(0, 6) as string[];
});

function formatMeta(job: PositionInfo) {
  return [job.city, job.district, job.experienceReq, job.educationReq].filter(Boolean).join(" · ") || "地点和要求待补充";
}

function formatSalary(job: PositionInfo) {
  if (!job.minSalary && !job.maxSalary) {
    return "薪资面议";
  }
  const min = job.minSalary ? Math.round(job.minSalary / 1000) : 0;
  const max = job.maxSalary ? Math.round(job.maxSalary / 1000) : 0;
  const months = job.salaryMonths && job.salaryMonths > 12 ? ` · ${job.salaryMonths}薪` : "";
  if (min && max) return `${min}-${max}K${months}`;
  if (min) return `${min}K起${months}`;
  return `${max}K以内${months}`;
}

function formatHighlight(job: PositionInfo) {
  // 1. 优先展示岗位描述，没有描述时展示岗位要求，最后给一个兜底提示。
  return job.jobDescription || job.jobRequirement || "岗位信息已发布，详细职责可联系招聘方进一步了解。";
}

function formatCompanyMeta(job: PositionInfo) {
  return [job.financingStage, job.companySize, job.companyIndustry].filter(Boolean).join(" · ") || "公司信息待补充";
}

function splitTags(value?: string) {
  if (!value) {
    return [];
  }
  return value
    .split(/[,，、]/)
    .map(item => item.trim())
    .filter(Boolean);
}

function estimateMatchScore(id: number | string) {
  // 1. 真实匹配分后续会由 AI 模块计算，这里先给列表一个稳定的展示值。
  return 82 + (Number(id) % 13);
}
</script>
