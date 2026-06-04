<template>
  <el-card shadow="never">
    <template #header>Agent 调用日志</template>
    <el-table
      border
      :data="[
        { traceId: 'JA-20260602-001', intent: 'JOB_MATCH', tool: 'JobMatchTool', cost: '1280ms', status: 'SUCCESS' },
        { traceId: 'JA-20260602-002', intent: 'RESUME_SCORE', tool: 'ResumeAnalyzeTool', cost: '960ms', status: 'SUCCESS' },
        { traceId: 'JA-20260602-003', intent: 'COMPANY_ANALYZE', tool: 'CompanyAnalyzeTool', cost: '2100ms', status: 'FAILED' }
      ]"
    >
      <el-table-column prop="traceId" label="TraceId" min-width="180" />
      <el-table-column prop="intent" label="意图" />
      <el-table-column prop="tool" label="工具" />
      <el-table-column prop="cost" label="耗时" />
      <el-table-column prop="status" label="状态" />
    </el-table>
  </el-card>
</template>
