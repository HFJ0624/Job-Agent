<script setup lang="ts">
import { onMounted, ref } from "vue";
import { ElMessage } from "element-plus";
import { getAdminDashboardOverview } from "../../api/dashboard";
import type {
  AdminDashboardMetric,
  AdminDashboardPendingItem,
  AdminDashboardSystemItem
} from "../../api/types";

const loading = ref(false);
const metrics = ref<AdminDashboardMetric[]>([]);
const pendingItems = ref<AdminDashboardPendingItem[]>([]);
const systemItems = ref<AdminDashboardSystemItem[]>([]);

function formatNumber(value: number) {
  return new Intl.NumberFormat("zh-CN").format(value || 0);
}

/**
 * 加载后台首页真实看板数据。
 *
 * 步骤:
 * 1. 请求后端聚合接口，避免首页前端分别调用多个列表接口造成慢加载。
 * 2. 将接口返回的三组数据分别写入指标卡、待处理事项和系统状态。
 * 3. 请求失败时保留空态并提示管理员，避免页面白屏。
 */
async function loadDashboard() {
  loading.value = true;
  try {
    const overview = await getAdminDashboardOverview();
    metrics.value = overview?.metrics || [];
    pendingItems.value = overview?.pendingItems || [];
    systemItems.value = overview?.systemItems || [];
  } catch (error) {
    console.error("[Job-Agent Admin] 加载首页看板失败", error);
    ElMessage.error("首页看板数据加载失败");
  } finally {
    loading.value = false;
  }
}

onMounted(loadDashboard);
</script>

<template>
  <section v-loading="loading" class="dashboard-grid">
    <el-card v-for="item in metrics" :key="item.label" shadow="never" class="metric-card">
      <span>{{ item.label }}</span>
      <strong>{{ formatNumber(item.value) }}</strong>
      <em>{{ item.subText }}</em>
    </el-card>
    <el-empty v-if="!loading && metrics.length === 0" description="暂无看板数据" />
  </section>

  <section class="admin-two-column">
    <el-card v-loading="loading" shadow="never">
      <template #header>今日待处理</template>
      <el-timeline v-if="pendingItems.length > 0">
        <el-timeline-item
          v-for="item in pendingItems"
          :key="item.title"
          :type="item.level === 'danger' ? 'danger' : item.level === 'warning' ? 'warning' : 'success'"
          :timestamp="item.title"
        >
          {{ item.content }}
        </el-timeline-item>
      </el-timeline>
      <el-empty v-else description="暂无待处理事项" />
    </el-card>

    <el-card v-loading="loading" shadow="never">
      <template #header>系统能力</template>
      <el-descriptions v-if="systemItems.length > 0" :column="1" border>
        <el-descriptions-item v-for="item in systemItems" :key="item.label" :label="item.label">
          {{ item.value }}
        </el-descriptions-item>
      </el-descriptions>
      <el-empty v-else description="暂无系统状态" />
    </el-card>
  </section>
</template>
