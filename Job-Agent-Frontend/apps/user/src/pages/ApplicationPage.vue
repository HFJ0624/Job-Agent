<template>
  <main class="page-section application-page">
    <section class="application-hero">
      <div>
        <p class="eyebrow">求职进度</p>
        <h1>管理你的投递、沟通和面试状态</h1>
        <p>把感兴趣的岗位加入进度，持续跟踪每个岗位的沟通、投递和面试进展。</p>
      </div>
      <button class="primary-button large" type="button" :disabled="loading" @click="loadData">
        {{ loading ? "刷新中..." : "刷新进度" }}
      </button>
    </section>

    <section class="stats-grid">
      <article class="stats-card">
        <span>总记录</span>
        <strong>{{ stats?.totalCount || 0 }}</strong>
      </article>

      <article class="stats-card warning">
        <span>今日需跟进</span>
        <strong>{{ stats?.todayFollowCount || 0 }}</strong>
      </article>

      <article class="stats-card primary">
        <span>面试中</span>
        <strong>{{ stats?.interviewingCount || 0 }}</strong>
      </article>
    </section>

    <section class="status-board">
      <button
        v-for="status in statusOptions"
        :key="status.value"
        type="button"
        class="status-card"
        :class="{ active: query.status === status.value }"
        @click="filterByStatus(status.value)"
      >
        <span>{{ status.label }}</span>
        <strong>{{ stats?.statusCountMap?.[status.value] || 0 }}</strong>
      </button>

      <button
        type="button"
        class="status-card"
        :class="{ active: !query.status }"
        @click="filterByStatus('')"
      >
        <span>全部</span>
        <strong>{{ stats?.totalCount || 0 }}</strong>
      </button>
    </section>

    <section class="filter-card">
      <label>
        <span>关键词</span>
        <input v-model.trim="query.keyword" placeholder="搜索岗位、公司或备注" />
      </label>

      <label>
        <span>城市</span>
        <input v-model.trim="query.city" placeholder="例如 上海" />
      </label>

      <label>
        <span>优先级</span>
        <select v-model="query.priority">
          <option value="">全部</option>
          <option value="HIGH">高</option>
          <option value="NORMAL">普通</option>
          <option value="LOW">低</option>
        </select>
      </label>

      <button class="primary-button" type="button" @click="handleSearch">
        查询
      </button>
    </section>

    <p v-if="errorMessage" class="form-error">{{ errorMessage }}</p>

    <section class="application-list">
      <p v-if="loading" class="empty-state">正在加载求职记录...</p>

      <p v-else-if="!applications.length" class="empty-state">
        暂无求职记录，可以先在岗位详情页点击“加入求职进度”。
      </p>

      <article
        v-for="item in applications"
        :key="item.id"
        class="application-item"
      >
        <div class="application-main">
          <div class="title-row">
            <h3>{{ item.jobTitle }}</h3>
            <span class="status-pill">{{ item.statusText }}</span>
            <span class="priority-pill">{{ item.priorityText || "普通" }}</span>
          </div>

          <p class="meta-line">
            {{ item.companyName || "未知公司" }} · {{ item.city || "城市待补充" }} · {{ item.salaryText || "薪资面议" }}
          </p>

          <p class="time-line">
            投递：{{ item.applyTime || "未投递" }}
            ｜ 面试：{{ item.interviewTime || "暂无" }}
            ｜ 跟进：{{ item.nextFollowTime || "暂无" }}
          </p>

          <p class="note-line">
            备注：{{ item.note || "暂无备注" }}
          </p>

          <p class="action-line">
            最近动作：{{ item.lastAction || "暂无" }}
          </p>
        </div>

        <div class="application-actions">
          <select v-model="item.status" @change="changeStatus(item)">
            <option v-for="status in statusOptions" :key="status.value" :value="status.value">
              {{ status.label }}
            </option>
          </select>

          <RouterLink class="secondary-button" :to="`/jobs/${item.jobId}`">
            查看岗位
          </RouterLink>

          <button class="danger-button" type="button" @click="removeApplication(item)">
            删除
          </button>
        </div>
      </article>
    </section>

    <div v-if="total > query.pageSize" class="pagination-row">
      <button
        class="secondary-button"
        type="button"
        :disabled="query.pageNum <= 1"
        @click="changePage(query.pageNum - 1)"
      >
        上一页
      </button>

      <span>第 {{ query.pageNum }} 页 / 共 {{ totalPages }} 页</span>

      <button
        class="secondary-button"
        type="button"
        :disabled="query.pageNum >= totalPages"
        @click="changePage(query.pageNum + 1)"
      >
        下一页
      </button>
    </div>
  </main>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { RouterLink } from "vue-router";
import { ElMessage, ElMessageBox } from "element-plus";
import {
  deleteApplication,
  getApplicationStats,
  pageApplications,
  updateApplicationStatus
} from "../api/application";
import type {
  JobApplicationInfo,
  JobApplicationStatsInfo
} from "../api/types";

/**
 * 状态选项。
 */
const statusOptions = [
  { value: "INTERESTED", label: "感兴趣" },
  { value: "COMMUNICATED", label: "已沟通" },
  { value: "APPLIED", label: "已投递" },
  { value: "INTERVIEWING", label: "面试中" },
  { value: "OFFER", label: "Offer" },
  { value: "REJECTED", label: "已拒绝" },
  { value: "CLOSED", label: "已结束" }
];

/**
 * 查询参数。
 */
const query = reactive({
  pageNum: 1,
  pageSize: 10,
  status: "",
  keyword: "",
  city: "",
  priority: ""
});

const applications = ref<JobApplicationInfo[]>([]);
const stats = ref<JobApplicationStatsInfo | null>(null);
const total = ref(0);
const loading = ref(false);
const errorMessage = ref("");

const totalPages = computed(() => {
  return Math.max(1, Math.ceil(total.value / query.pageSize));
});

onMounted(loadData);

/**
 * 加载统计和列表。
 */
async function loadData() {
  loading.value = true;
  errorMessage.value = "";

  try {
    const [statsResult, pageResult] = await Promise.all([
      getApplicationStats(),
      pageApplications(query)
    ]);

    stats.value = statsResult;
    applications.value = pageResult.records || [];
    total.value = pageResult.total || 0;
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : "求职进度加载失败";
    ElMessage.error(errorMessage.value);
  } finally {
    loading.value = false;
  }
}

/**
 * 按状态筛选。
 */
function filterByStatus(status: string) {
  query.status = status;
  query.pageNum = 1;
  loadData();
}

/**
 * 搜索。
 */
function handleSearch() {
  query.pageNum = 1;
  loadData();
}

/**
 * 翻页。
 */
function changePage(page: number) {
  query.pageNum = page;
  loadData();
}

/**
 * 修改状态。
 */
async function changeStatus(item: JobApplicationInfo) {
  try {
    const updated = await updateApplicationStatus(item.id, {
      status: item.status
    });

    Object.assign(item, updated);
    ElMessage.success("状态已更新");

    stats.value = await getApplicationStats();
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : "状态更新失败");
    await loadData();
  }
}

/**
 * 删除记录。
 */
async function removeApplication(item: JobApplicationInfo) {
  try {
    await ElMessageBox.confirm(
      `确定删除「${item.jobTitle}」的求职记录吗？`,
      "删除求职记录",
      {
        type: "warning",
        confirmButtonText: "删除",
        cancelButtonText: "取消"
      }
    );

    await deleteApplication(item.id);
    ElMessage.success("求职记录已删除");
    await loadData();
  } catch (error) {
    if (error === "cancel" || error === "close") {
      return;
    }

    ElMessage.error(error instanceof Error ? error.message : "删除失败");
  }
}
</script>

<style scoped>
.application-page {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.application-hero,
.filter-card {
  padding: 24px;
  border-radius: 22px;
  background: #ffffff;
  border: 1px solid #e5e7eb;
}

.application-hero {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: linear-gradient(135deg, #eff6ff, #ffffff);
}

.application-hero h1 {
  margin: 6px 0;
  color: #111827;
}

.application-hero p {
  color: #6b7280;
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
}

.stats-card {
  padding: 18px;
  border-radius: 18px;
  background: #ffffff;
  border: 1px solid #e5e7eb;
}

.stats-card span {
  color: #6b7280;
}

.stats-card strong {
  display: block;
  margin-top: 8px;
  font-size: 28px;
  color: #111827;
}

.stats-card.warning {
  background: #fff7ed;
  border-color: #fed7aa;
}

.stats-card.primary {
  background: #eff6ff;
  border-color: #bfdbfe;
}

.status-board {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.status-card {
  padding: 14px;
  border-radius: 16px;
  border: 1px solid #e5e7eb;
  background: #ffffff;
  cursor: pointer;
  text-align: left;
}

.status-card.active {
  border-color: #2563eb;
  background: #eff6ff;
}

.status-card span {
  color: #6b7280;
}

.status-card strong {
  display: block;
  margin-top: 6px;
  color: #111827;
  font-size: 22px;
}

.filter-card {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr)) auto;
  align-items: end;
  gap: 14px;
}

.filter-card label {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.filter-card span {
  color: #374151;
  font-size: 13px;
  font-weight: 600;
}

.filter-card input,
.filter-card select,
.application-actions select {
  padding: 10px 12px;
  border-radius: 12px;
  border: 1px solid #d1d5db;
  font: inherit;
}

.application-list {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.application-item {
  display: flex;
  justify-content: space-between;
  gap: 18px;
  padding: 20px;
  border-radius: 18px;
  background: #ffffff;
  border: 1px solid #e5e7eb;
}

.application-main {
  flex: 1;
}

.title-row {
  display: flex;
  align-items: center;
  gap: 10px;
}

.title-row h3 {
  margin: 0;
  color: #111827;
}

.status-pill,
.priority-pill {
  padding: 4px 9px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 700;
}

.status-pill {
  background: #ecfdf5;
  color: #047857;
}

.priority-pill {
  background: #eff6ff;
  color: #2563eb;
}

.meta-line,
.time-line,
.note-line,
.action-line {
  margin: 8px 0;
  color: #6b7280;
}

.application-actions {
  display: flex;
  flex-direction: column;
  gap: 10px;
  min-width: 130px;
}

.pagination-row {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 14px;
}

@media (max-width: 900px) {
  .stats-grid,
  .status-board,
  .filter-card {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .application-item {
    flex-direction: column;
  }

  .application-actions {
    flex-direction: row;
    flex-wrap: wrap;
  }
}

@media (max-width: 640px) {
  .stats-grid,
  .status-board,
  .filter-card {
    grid-template-columns: 1fr;
  }

  .application-hero {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>