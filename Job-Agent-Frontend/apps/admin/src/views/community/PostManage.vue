<template>
  <el-card shadow="never">
    <template #header>社区内容管理</template>
    <el-table
      border
      :data="[
        { title: 'Java 后端面试复盘', type: '经验分享', likes: 128, status: '已发布' },
        { title: '这份简历该怎么改', type: '简历互评', likes: 42, status: '待审核' },
        { title: '外包岗位避坑提醒', type: '避坑帖', likes: 256, status: '已发布' }
      ]"
    >
      <el-table-column prop="title" label="标题" min-width="220" />
      <el-table-column prop="type" label="类型" />
      <el-table-column prop="likes" label="点赞数" />
      <el-table-column prop="status" label="状态" />
    </el-table>
  </el-card>
</template>
