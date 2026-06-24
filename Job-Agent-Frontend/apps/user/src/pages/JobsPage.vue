<template>
  <main class="page-section">
    <div class="list-toolbar">
      <div>
        <p class="eyebrow">职位搜索</p>
        <h1>根据偏好筛选岗位</h1>
      </div>
      <div class="search-panel compact">
        <input
          v-model.trim="query.keyword"
          aria-label="职位关键词"
          placeholder="Java 后端 / AI 应用 / RAG"
          @keyup.enter="search"
        />
        <button @click="search">搜索</button>
      </div>
    </div>

    <div class="jobs-layout">
      <aside class="filter-panel job-filter-panel">
        <div class="filter-panel-heading">
          <h3>筛选条件</h3>
          <button class="text-button" type="button" @click="resetFilters">重置</button>
        </div>

        <p class="filter-note">仅展示后台已发布岗位，每页展示 10 个职位。</p>

        <section v-for="group in filterGroups" :key="group.field" class="filter-group">
          <h4>{{ group.title }}</h4>
          <div class="filter-options">
            <button
              v-for="option in group.options"
              :key="option.value || 'all'"
              class="filter-chip"
              :class="{ active: query[group.field] === option.value }"
              type="button"
              @click="selectFilter(group.field, option.value)"
            >
              {{ option.label }}
            </button>
          </div>
        </section>

        <section class="filter-group">
          <h4>区县/商圈</h4>
          <div class="filter-inline-search">
            <input v-model.trim="query.district" placeholder="例如 浦东新区 / 西湖区" @keyup.enter="search" />
            <button type="button" @click="search">应用</button>
          </div>
        </section>
      </aside>

      <section class="job-list">
        <div class="job-list-header">
          <div>
            <p class="eyebrow">岗位列表</p>
            <h2>共 {{ total }} 个岗位</h2>
          </div>
          <button type="button" class="text-button" @click="loadJobs">刷新</button>
        </div>

        <p v-if="loading" class="empty-state">正在加载岗位列表...</p>
        <p v-else-if="errorMessage" class="form-error">{{ errorMessage }}</p>
        <p v-else-if="!jobs.length" class="empty-state">暂无符合条件的已发布岗位，可以调整筛选后再试。</p>
        <template v-else>
          <JobCard v-for="job in jobs" :key="job.id" :job="job" />
        </template>

        <div v-if="total > 0" class="job-pagination">
          <el-pagination
            v-model:current-page="query.pageNo"
            background
            layout="prev, pager, next, total"
            :page-size="query.pageSize"
            :total="total"
            @current-change="handlePageChange"
          />
        </div>
      </section>
    </div>
  </main>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import { useRoute } from "vue-router";
import { pageFrontPositions } from "../api/job";
import type { PositionInfo } from "../api/types";
import JobCard from "../components/JobCard.vue";

type FilterField = "city" | "jobCategory" | "educationReq" | "experienceReq" | "workType";

interface JobQuery {
  pageNo: number;
  pageSize: number;
  keyword: string;
  city: string;
  district: string;
  jobCategory: string;
  educationReq: string;
  experienceReq: string;
  workType: string;
}

const jobs = ref<PositionInfo[]>([]);
const total = ref(0);
const loading = ref(false);
const errorMessage = ref("");
const route = useRoute();

const query = reactive<JobQuery>({
  pageNo: 1,
  pageSize: 10,
  keyword: "",
  city: "",
  district: "",
  jobCategory: "",
  educationReq: "",
  experienceReq: "",
  workType: ""
});

const filterGroups: Array<{
  title: string;
  field: FilterField;
  options: Array<{ label: string; value: string }>;
}> = [
  {
    title: "工作城市",
    field: "city",
    options: [
      { label: "全部", value: "" },
      { label: "上海", value: "上海" },
      { label: "杭州", value: "杭州" },
      { label: "深圳", value: "深圳" },
      { label: "北京", value: "北京" },
      { label: "广州", value: "广州" },
      { label: "成都", value: "成都" },
      { label: "西安", value: "西安" },
      { label: "武汉", value: "武汉" }
    ]
  },
  {
    title: "职位类别",
    field: "jobCategory",
    options: [
      { label: "全部", value: "" },
      { label: "后端开发", value: "后端开发" },
      { label: "前端开发", value: "前端开发" },
      { label: "AI 应用", value: "AI 应用" },
      { label: "产品运营", value: "产品运营" },
      { label: "测试", value: "测试" },
      { label: "运维", value: "运维" },
      { label: "财务", value: "财务" }
    ]
  },
  {
    title: "学历要求",
    field: "educationReq",
    options: [
      { label: "全部", value: "" },
      { label: "不限", value: "不限" },
      { label: "大专", value: "大专" },
      { label: "本科", value: "本科" },
      { label: "本科及以上", value: "本科及以上" },
      { label: "硕士", value: "硕士" }
    ]
  },
  {
    title: "经验要求",
    field: "experienceReq",
    options: [
      { label: "全部", value: "" },
      { label: "不限", value: "不限" },
      { label: "1年以内", value: "1年以内" },
      { label: "1-3年", value: "1-3年" },
      { label: "3-5年", value: "3-5年" },
      { label: "5-10年", value: "5-10年" }
    ]
  },
  {
    title: "工作类型",
    field: "workType",
    options: [
      { label: "全部", value: "" },
      { label: "全职", value: "全职" },
      { label: "实习", value: "实习" },
      { label: "远程", value: "远程" },
      { label: "兼职", value: "兼职" }
    ]
  }
];

async function loadJobs() {
  loading.value = true;
  errorMessage.value = "";
  try {
    // 1. 前台接口会在后端强制 status=1，这里只负责传分页和筛选条件，草稿岗位不会展示给用户。
    const page = await pageFrontPositions({
      pageNo: query.pageNo,
      pageSize: query.pageSize,
      keyword: query.keyword,
      city: query.city,
      district: query.district,
      jobCategory: query.jobCategory,
      educationReq: query.educationReq,
      experienceReq: query.experienceReq,
      workType: query.workType
    });
    jobs.value = page.records;
    total.value = Number(page.total || 0);
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : "岗位列表加载失败";
  } finally {
    loading.value = false;
  }
}

function search() {
  // 1. 搜索或修改筛选条件时回到第一页，避免当前页码超过新结果的总页数。
  query.pageNo = 1;
  loadJobs();
}

function selectFilter(field: FilterField, value: string) {
  query[field] = value;
  search();
}

function resetFilters() {
  query.keyword = "";
  query.city = "";
  query.district = "";
  query.jobCategory = "";
  query.educationReq = "";
  query.experienceReq = "";
  query.workType = "";
  search();
}

function handlePageChange(pageNo: number) {
  query.pageNo = pageNo;
  loadJobs();
}

onMounted(() => {
  // 1. 首页搜索会通过 /jobs?keyword=xxx 跳转到岗位列表，这里读取 query 后再加载真实筛选结果。
  if (typeof route.query.keyword === "string") {
    query.keyword = route.query.keyword;
  }
  loadJobs();
});
</script>
