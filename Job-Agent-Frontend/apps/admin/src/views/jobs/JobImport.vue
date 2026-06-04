<template>
  <el-card shadow="never">
    <template #header>岗位导入</template>
    <el-upload drag action="#" :auto-upload="false">
      <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
      <div class="el-upload__text">拖拽 Excel 文件到这里，或点击选择文件</div>
      <template #tip>
        <div class="el-upload__tip">后续可以接入后端导入接口，批量录入公司和岗位数据。</div>
      </template>
    </el-upload>
  </el-card>
</template>

<script setup lang="ts">
import { UploadFilled } from "@element-plus/icons-vue";
</script>
