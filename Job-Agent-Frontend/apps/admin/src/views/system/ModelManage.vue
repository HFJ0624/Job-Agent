<template>
  <el-card shadow="never">
    <template #header>模型配置</template>
    <el-descriptions :column="1" border>
      <el-descriptions-item label="主模型">OpenAI 兼容 ChatModel</el-descriptions-item>
      <el-descriptions-item label="备用模型">Ollama 本地模型</el-descriptions-item>
      <el-descriptions-item label="路由策略">按任务类型选择模型，失败后自动降级</el-descriptions-item>
    </el-descriptions>
  </el-card>
</template>
