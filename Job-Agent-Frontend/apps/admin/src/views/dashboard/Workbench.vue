<script setup lang="ts">
const metrics = [
  { label: "注册用户", value: "12,480", trend: "+18%" },
  { label: "在招岗位", value: "3,268", trend: "+9%" },
  { label: "简历解析", value: "8,921", trend: "+26%" },
  { label: "Agent 调用", value: "42,190", trend: "+31%" }
];
</script>

<template>
  <section class="dashboard-grid">
    <el-card v-for="item in metrics" :key="item.label" shadow="never" class="metric-card">
      <span>{{ item.label }}</span>
      <strong>{{ item.value }}</strong>
      <em>{{ item.trend }}</em>
    </el-card>
  </section>

  <section class="admin-two-column">
    <el-card shadow="never">
      <template #header>今日待处理</template>
      <el-timeline>
        <el-timeline-item timestamp="09:20">审核 12 条新岗位</el-timeline-item>
        <el-timeline-item timestamp="10:00">检查 3 条 Agent 失败日志</el-timeline-item>
        <el-timeline-item timestamp="14:30">更新 Java 后端岗位推荐规则</el-timeline-item>
      </el-timeline>
    </el-card>

    <el-card shadow="never">
      <template #header>系统能力</template>
      <el-descriptions :column="1" border>
        <el-descriptions-item label="后台菜单">固定菜单配置</el-descriptions-item>
        <el-descriptions-item label="AI 模块">简历评分、岗位匹配、打招呼语</el-descriptions-item>
        <el-descriptions-item label="数据闭环">用户、简历、岗位、Agent Trace</el-descriptions-item>
      </el-descriptions>
    </el-card>
  </section>
</template>
