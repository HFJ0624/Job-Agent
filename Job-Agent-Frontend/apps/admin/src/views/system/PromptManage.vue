<template>
  <el-card shadow="never">
    <template #header>
      <div class="card-header">
        <span>Prompt 管理</span>
        <el-button type="primary">新增模板</el-button>
      </div>
    </template>

    <el-table
      border
      :data="[
        { code: 'RESUME_SCORE', name: '简历评分 Prompt', version: 'v1.0' },
        { code: 'JOB_MATCH', name: '岗位匹配 Prompt', version: 'v1.1' },
        { code: 'GREETING_GENERATE', name: '打招呼语 Prompt', version: 'v1.0' }
      ]"
    >
      <el-table-column prop="code" label="编码" />
      <el-table-column prop="name" label="名称" />
      <el-table-column prop="version" label="版本" />
    </el-table>
  </el-card>
</template>
