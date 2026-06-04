<script setup lang="ts">
const jobs = [
  { title: "Java 后端开发工程师", company: "星河智能科技", city: "上海", salary: "18-28K", status: "上架" },
  { title: "AI 应用开发工程师", company: "青岚数据", city: "杭州", salary: "20-35K", status: "上架" },
  { title: "全栈开发工程师", company: "一象简历", city: "北京", salary: "16-26K", status: "草稿" }
];
</script>

<template>
  <el-card shadow="never">
    <template #header>
      <div class="card-header">
        <span>岗位列表</span>
        <el-button type="primary">发布岗位</el-button>
      </div>
    </template>

    <el-table :data="jobs" border>
      <el-table-column prop="title" label="岗位名称" min-width="180" />
      <el-table-column prop="company" label="公司" />
      <el-table-column prop="city" label="城市" />
      <el-table-column prop="salary" label="薪资" />
      <el-table-column prop="status" label="状态" />
    </el-table>
  </el-card>
</template>
