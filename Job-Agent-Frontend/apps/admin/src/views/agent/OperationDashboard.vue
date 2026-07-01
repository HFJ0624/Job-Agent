<template>
  <main class="operation-page">
    <section class="page-header">
      <div>
        <p class="eyebrow">Agent Operations</p>
        <h1>Agent 运营看板</h1>
        <p>查看最近 7 天 AI 日报、邮件发送、行动项完成率和失败分布，帮助管理员发现 Agent 是否真正推动了用户行动。</p>
      </div>
      <div class="header-actions">
        <el-button type="primary" :loading="loading" @click="loadDashboard">刷新看板</el-button>
      </div>
    </section>

    <section class="metric-grid">
      <article v-for="item in dashboard.metrics" :key="item.label" class="metric-card" :class="item.level">
        <span>{{ item.label }}</span>
        <strong>{{ formatNumber(item.value) }}</strong>
        <small>{{ item.subText || "-" }}</small>
      </article>
    </section>

    <section class="dashboard-grid">
      <article class="panel">
        <div class="panel-title">
          <h2>AI 日报与邮件</h2>
          <span>生成、失败和发送结果</span>
        </div>
        <el-table v-loading="loading" :data="dashboard.reportStats" border stripe empty-text="暂无日报统计">
          <el-table-column prop="name" label="指标" min-width="160" />
          <el-table-column prop="count" label="数量" width="100" />
          <el-table-column label="占比" width="130">
            <template #default="{ row }">{{ formatPercent(row.ratio) }}</template>
          </el-table-column>
        </el-table>
      </article>

      <article class="panel">
        <div class="panel-title">
          <h2>行动项状态</h2>
          <span>行动项整体完成情况</span>
        </div>
        <el-table v-loading="loading" :data="dashboard.actionStatusStats" border stripe empty-text="暂无行动项统计">
          <el-table-column prop="name" label="状态" min-width="160" />
          <el-table-column prop="count" label="数量" width="100" />
          <el-table-column label="占比" width="130">
            <template #default="{ row }">{{ formatPercent(row.ratio) }}</template>
          </el-table-column>
        </el-table>
      </article>

      <article class="panel">
        <div class="panel-title">
          <h2>行动项来源效果</h2>
          <span>不同来源产生的行动项占比</span>
        </div>
        <el-table v-loading="loading" :data="dashboard.actionSourceStats" border stripe empty-text="暂无来源统计">
          <el-table-column prop="name" label="来源" min-width="160" />
          <el-table-column prop="count" label="数量" width="100" />
          <el-table-column label="占比" width="130">
            <template #default="{ row }">{{ formatPercent(row.ratio) }}</template>
          </el-table-column>
        </el-table>
      </article>

      <article class="panel">
        <div class="panel-title">
          <h2>ActionType 失败排行</h2>
          <span>优先定位失败最多的动作类型</span>
        </div>
        <el-table v-loading="loading" :data="dashboard.actionTypeFailureStats" border stripe empty-text="暂无失败排行">
          <el-table-column prop="name" label="动作类型" min-width="160" />
          <el-table-column prop="count" label="失败数" width="100" />
          <el-table-column label="占比" width="130">
            <template #default="{ row }">{{ formatPercent(row.ratio) }}</template>
          </el-table-column>
        </el-table>
      </article>
    </section>

    <section class="panel full-panel">
      <div class="panel-title">
        <h2>最近失败记录</h2>
        <span>包含日报生成、邮件发送和行动项执行失败</span>
      </div>
      <el-table v-loading="loading" :data="dashboard.recentFailures" border stripe empty-text="最近没有失败记录">
        <el-table-column prop="failureType" label="失败类型" width="150" />
        <el-table-column prop="userId" label="用户ID" width="100" />
        <el-table-column prop="title" label="标题" min-width="180" show-overflow-tooltip />
        <el-table-column prop="reason" label="失败原因" min-width="260" show-overflow-tooltip />
        <el-table-column prop="createTime" label="时间" width="180" />
      </el-table>
    </section>
  </main>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import { ElMessage } from "element-plus";
import { getAgentOperationDashboard } from "../../api/agentOperation";
import type { AgentOperationDashboard } from "../../api/types";

const loading = ref(false);

const dashboard = reactive<AgentOperationDashboard>({
  metrics: [],
  reportStats: [],
  actionStatusStats: [],
  actionSourceStats: [],
  actionTypeFailureStats: [],
  recentFailures: []
});

onMounted(() => {
  loadDashboard();
});

async function loadDashboard() {
  loading.value = true;
  try {
    const data = await getAgentOperationDashboard();
    /*
     * 后端返回完整聚合对象，前端只做展示态赋值。
     * 这样统计口径统一留在服务端，后续做时间筛选或预聚合时不需要改页面计算逻辑。
     */
    Object.assign(dashboard, {
      metrics: data?.metrics || [],
      reportStats: data?.reportStats || [],
      actionStatusStats: data?.actionStatusStats || [],
      actionSourceStats: data?.actionSourceStats || [],
      actionTypeFailureStats: data?.actionTypeFailureStats || [],
      recentFailures: data?.recentFailures || []
    });
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : "Agent 运营看板加载失败");
  } finally {
    loading.value = false;
  }
}

function formatNumber(value?: number) {
  return Number(value || 0).toLocaleString();
}

function formatPercent(value?: number) {
  return `${Number(value || 0).toFixed(2)}%`;
}
</script>

<style scoped>
.operation-page {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 20px 22px;
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 8px 24px rgb(15 23 42 / 6%);
}

.eyebrow {
  margin: 0 0 6px;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
  color: #2563eb;
  text-transform: uppercase;
}

.page-header h1 {
  margin: 0;
  font-size: 24px;
  color: #111827;
}

.page-header p:last-child {
  margin: 8px 0 0;
  color: #64748b;
}

.metric-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
}

.metric-card {
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-height: 112px;
  padding: 18px;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
}

.metric-card span {
  color: #64748b;
  font-size: 13px;
}

.metric-card strong {
  color: #111827;
  font-size: 28px;
  line-height: 1;
}

.metric-card small {
  color: #94a3b8;
}

.metric-card.success {
  border-color: #bbf7d0;
}

.metric-card.warning {
  border-color: #fde68a;
}

.metric-card.danger {
  border-color: #fecaca;
}

.dashboard-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.panel {
  padding: 18px;
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 8px 24px rgb(15 23 42 / 6%);
}

.full-panel {
  width: 100%;
}

.panel-title {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
}

.panel-title h2 {
  margin: 0;
  font-size: 17px;
  color: #111827;
}

.panel-title span {
  color: #94a3b8;
  font-size: 13px;
}

@media (max-width: 1200px) {
  .metric-grid,
  .dashboard-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 760px) {
  .page-header {
    align-items: flex-start;
    flex-direction: column;
  }

  .metric-grid,
  .dashboard-grid {
    grid-template-columns: 1fr;
  }
}
</style>
