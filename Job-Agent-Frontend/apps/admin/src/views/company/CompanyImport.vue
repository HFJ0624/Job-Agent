<script setup lang="ts">
import { computed, ref } from "vue";
import { useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import type { UploadFile, UploadUserFile } from "element-plus";
import { UploadFilled } from "@element-plus/icons-vue";
import { importCompaniesApi } from "../../api/company";
import type { CompanyImportResult } from "../../api/types";

const router = useRouter();
const uploading = ref(false);
const fileList = ref<UploadUserFile[]>([]);
const importResult = ref<CompanyImportResult | null>(null);

const selectedFile = computed(() => fileList.value[0]?.raw);

function handleFileChange(uploadFile: UploadFile) {
  // 1. 只保留最新选择的一个文件，避免用户一次导入多个 Excel 导致结果难以判断。
  fileList.value = uploadFile.raw ? [uploadFile as UploadUserFile] : [];
  importResult.value = null;
}

function handleFileRemove() {
  fileList.value = [];
  importResult.value = null;
}

async function submitImport() {
  const file = selectedFile.value;
  if (!file) {
    ElMessage.warning("请先选择 Excel 文件");
    return;
  }

  if (!isExcelFile(file.name)) {
    ElMessage.warning("只支持 xls 或 xlsx 文件");
    return;
  }

  uploading.value = true;
  try {
    // 1. 真正导入动作由后端完成，前端只负责上传文件和展示统计结果。
    importResult.value = await importCompaniesApi(file);
    ElMessage.success("公司 Excel 导入完成");
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : "公司 Excel 导入失败");
  } finally {
    uploading.value = false;
  }
}

function isExcelFile(filename: string) {
  return /\.(xls|xlsx)$/i.test(filename);
}

function goList() {
  router.push("/company/list");
}
</script>

<template>
  <el-card shadow="never">
    <template #header>
      <div class="card-header">
        <span>公司导入</span>
        <el-button @click="goList">返回公司列表</el-button>
      </div>
    </template>

    <el-alert
      title="Excel 第一行必须是表头，推荐使用截图中的字段：company_name、logo_url、industry、company_size、financing_stage、description、province、city、district、address、longitude、latitude、prospect_score、status。"
      type="info"
      show-icon
      :closable="false"
      class="table-alert"
    />

    <el-upload
      v-model:file-list="fileList"
      drag
      action="#"
      accept=".xls,.xlsx"
      :auto-upload="false"
      :limit="1"
      :on-change="handleFileChange"
      :on-remove="handleFileRemove"
    >
      <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
      <div class="el-upload__text">拖拽 Excel 文件到这里，或点击选择文件</div>
      <template #tip>
        <div class="el-upload__tip">重复公司名称会自动更新，不会插入重复公司。单次最多导入 5000 行。</div>
      </template>
    </el-upload>

    <div class="import-actions">
      <el-button type="primary" :loading="uploading" :disabled="!selectedFile" @click="submitImport">
        开始导入
      </el-button>
      <el-button @click="handleFileRemove">清空文件</el-button>
    </div>

    <section v-if="importResult" class="import-result">
      <el-card shadow="never">
        <template #header>导入结果</template>
        <div class="import-result-grid">
          <div>
            <span>读取行数</span>
            <strong>{{ importResult.totalRows }}</strong>
          </div>
          <div>
            <span>新增公司</span>
            <strong>{{ importResult.insertCount }}</strong>
          </div>
          <div>
            <span>更新公司</span>
            <strong>{{ importResult.updateCount }}</strong>
          </div>
          <div>
            <span>失败行数</span>
            <strong>{{ importResult.failureCount }}</strong>
          </div>
        </div>

        <el-alert
          v-if="importResult.failureMessages.length"
          title="以下行导入失败，请修改 Excel 后重新导入"
          type="warning"
          show-icon
          :closable="false"
          class="table-alert"
        />
        <el-scrollbar v-if="importResult.failureMessages.length" height="180px">
          <ul class="failure-list">
            <li v-for="message in importResult.failureMessages" :key="message">{{ message }}</li>
          </ul>
        </el-scrollbar>
      </el-card>
    </section>
  </el-card>
</template>
