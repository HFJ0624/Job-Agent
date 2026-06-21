<template>
  <div class="prompt-page">
    <div class="page-header">
      <div>
        <div class="eyebrow">Prompt Console</div>
        <h1>Prompt 管理</h1>
      </div>
      <el-button type="primary" @click="openTemplateDialog()">新增模板</el-button>
    </div>

    <el-card shadow="never">
      <el-form :model="templateQuery" inline>
        <el-form-item label="Prompt 编码">
          <el-input v-model="templateQuery.promptCode" clearable placeholder="如 AGENT_SUMMARY" />
        </el-form-item>
        <el-form-item label="业务场景">
          <el-input v-model="templateQuery.sceneCode" clearable placeholder="如 AGENT_SUMMARY" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="templateQuery.status" clearable placeholder="全部" style="width: 140px">
            <el-option label="启用" value="ACTIVE" />
            <el-option label="停用" value="DISABLED" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="loadTemplates">查询</el-button>
          <el-button @click="resetTemplateQuery">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table v-loading="templateLoading" border :data="templates" @row-click="selectTemplate">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="promptCode" label="Prompt 编码" min-width="180" />
        <el-table-column prop="promptName" label="名称" min-width="180" />
        <el-table-column prop="sceneCode" label="业务场景" min-width="160" />
        <el-table-column prop="description" label="说明" min-width="220" show-overflow-tooltip />
        <el-table-column prop="status" label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="240" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click.stop="selectTemplate(row)">版本</el-button>
            <el-button link type="primary" @click.stop="openTemplateDialog(row)">编辑</el-button>
            <el-popconfirm title="确认删除这个 Prompt 模板？" @confirm="removeTemplate(row)">
              <template #reference>
                <el-button link type="danger" @click.stop>删除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>

      <div class="pager">
        <el-pagination
          v-model:current-page="templateQuery.pageNum"
          v-model:page-size="templateQuery.pageSize"
          background
          layout="total, sizes, prev, pager, next"
          :total="templateTotal"
          @size-change="loadTemplates"
          @current-change="loadTemplates"
        />
      </div>
    </el-card>

    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>Prompt 版本</span>
          <el-button type="primary" :disabled="!selectedTemplate" @click="openVersionDialog()">新增版本</el-button>
        </div>
      </template>

      <el-empty v-if="!selectedTemplate" description="先选择一个 Prompt 模板" />
      <template v-else>
        <div class="selected-template">
          当前模板：{{ selectedTemplate.promptCode }} / {{ selectedTemplate.promptName }}
        </div>
        <el-table v-loading="versionLoading" border :data="versions">
          <el-table-column prop="id" label="ID" width="80" />
          <el-table-column prop="versionNo" label="版本号" width="120" />
          <el-table-column prop="title" label="标题" min-width="180" />
          <el-table-column prop="status" label="状态" width="120">
            <template #default="{ row }">
              <el-tag :type="versionTagType(row.status)">{{ row.status }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="grayPercent" label="灰度" width="100">
            <template #default="{ row }">{{ row.grayPercent ?? 100 }}%</template>
          </el-table-column>
          <el-table-column prop="abGroup" label="A/B" width="100" />
          <el-table-column prop="publishTime" label="发布时间" width="180" />
          <el-table-column prop="content" label="内容预览" min-width="280" show-overflow-tooltip />
          <el-table-column label="操作" width="260" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="openVersionDialog(row)">编辑</el-button>
              <el-button link type="success" :disabled="row.status === 'PUBLISHED'" @click="publishVersion(row)">
                发布
              </el-button>
              <el-button link type="warning" :disabled="row.status === 'ARCHIVED'" @click="archiveVersion(row)">
                归档
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </template>
    </el-card>

    <el-dialog v-model="templateDialogVisible" :title="templateForm.id ? '编辑模板' : '新增模板'" width="620px">
      <el-form ref="templateFormRef" :model="templateForm" :rules="templateRules" label-width="110px">
        <el-form-item label="Prompt 编码" prop="promptCode">
          <el-input v-model="templateForm.promptCode" placeholder="AGENT_SUMMARY" />
        </el-form-item>
        <el-form-item label="名称" prop="promptName">
          <el-input v-model="templateForm.promptName" placeholder="Agent 执行总结 Prompt" />
        </el-form-item>
        <el-form-item label="业务场景" prop="sceneCode">
          <el-input v-model="templateForm.sceneCode" placeholder="AGENT_SUMMARY" />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="templateForm.status">
            <el-radio-button label="ACTIVE">启用</el-radio-button>
            <el-radio-button label="DISABLED">停用</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="templateForm.description" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="templateDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveTemplate">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="versionDialogVisible" :title="versionForm.id ? '编辑版本' : '新增版本'" width="900px">
      <el-form ref="versionFormRef" :model="versionForm" :rules="versionRules" label-width="110px">
        <el-form-item label="版本号" prop="versionNo">
          <el-input v-model="versionForm.versionNo" placeholder="v1.0.0" />
        </el-form-item>
        <el-form-item label="标题" prop="title">
          <el-input v-model="versionForm.title" placeholder="Agent 总结第一版" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="versionForm.status" style="width: 180px">
            <el-option label="草稿" value="DRAFT" />
            <el-option label="已发布" value="PUBLISHED" />
            <el-option label="已归档" value="ARCHIVED" />
          </el-select>
        </el-form-item>
        <el-form-item label="灰度 / A-B">
          <div class="inline-fields">
            <el-input-number v-model="versionForm.grayPercent" :min="0" :max="100" />
            <el-input v-model="versionForm.abGroup" clearable placeholder="A/B 分组，可空" />
          </div>
        </el-form-item>
        <el-form-item label="变量说明">
          <el-input v-model="versionForm.variablesJson" type="textarea" :rows="4" placeholder='{"currentUserInput":"当前用户输入"}' />
        </el-form-item>
        <el-form-item label="Prompt 内容" prop="content">
          <el-input
            v-model="versionForm.content"
            type="textarea"
            :rows="16"
            placeholder="支持 {{currentUserInput}}、{{planJson}}、{{executionResultJson}} 等变量"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="versionDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveVersion">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import { ElMessage, type FormInstance, type FormRules } from "element-plus";
import {
  archivePromptVersion,
  createPromptTemplate,
  createPromptVersion,
  deletePromptTemplate,
  listPromptVersions,
  pagePromptTemplates,
  publishPromptVersion,
  updatePromptTemplate,
  updatePromptVersion
} from "../../api/aiPrompt";
import type { AiPromptTemplateInfo, AiPromptVersionInfo } from "../../api/types";

const templateLoading = ref(false);
const versionLoading = ref(false);
const saving = ref(false);
const templates = ref<AiPromptTemplateInfo[]>([]);
const versions = ref<AiPromptVersionInfo[]>([]);
const templateTotal = ref(0);
const selectedTemplate = ref<AiPromptTemplateInfo | null>(null);
const templateDialogVisible = ref(false);
const versionDialogVisible = ref(false);
const templateFormRef = ref<FormInstance>();
const versionFormRef = ref<FormInstance>();

const templateQuery = reactive({
  pageNum: 1,
  pageSize: 10,
  promptCode: "",
  sceneCode: "",
  status: ""
});

const templateForm = reactive<AiPromptTemplateInfo>({
  promptCode: "",
  promptName: "",
  sceneCode: "",
  description: "",
  status: "ACTIVE"
});

const versionForm = reactive<AiPromptVersionInfo>({
  templateId: 0,
  versionNo: "",
  title: "",
  content: "",
  variablesJson: "",
  status: "DRAFT",
  grayPercent: 100,
  abGroup: ""
});

const templateRules: FormRules = {
  promptCode: [{ required: true, message: "请输入 Prompt 编码", trigger: "blur" }],
  promptName: [{ required: true, message: "请输入名称", trigger: "blur" }],
  sceneCode: [{ required: true, message: "请输入业务场景", trigger: "blur" }]
};

const versionRules: FormRules = {
  versionNo: [{ required: true, message: "请输入版本号", trigger: "blur" }],
  title: [{ required: true, message: "请输入标题", trigger: "blur" }],
  content: [{ required: true, message: "请输入 Prompt 内容", trigger: "blur" }]
};

onMounted(() => {
  loadTemplates();
});

async function loadTemplates() {
  templateLoading.value = true;
  try {
    const result = await pagePromptTemplates(templateQuery);
    templates.value = result.records || [];
    templateTotal.value = result.total || 0;
    if (selectedTemplate.value) {
      const latest = templates.value.find((item) => item.id === selectedTemplate.value?.id);
      selectedTemplate.value = latest || null;
    }
  } finally {
    templateLoading.value = false;
  }
}

function resetTemplateQuery() {
  templateQuery.pageNum = 1;
  templateQuery.promptCode = "";
  templateQuery.sceneCode = "";
  templateQuery.status = "";
  loadTemplates();
}

async function selectTemplate(row: AiPromptTemplateInfo) {
  selectedTemplate.value = row;
  await loadVersions(row.id as number);
}

async function loadVersions(templateId: number) {
  versionLoading.value = true;
  try {
    versions.value = await listPromptVersions(templateId);
  } finally {
    versionLoading.value = false;
  }
}

function openTemplateDialog(row?: AiPromptTemplateInfo) {
  Object.assign(templateForm, {
    id: row?.id,
    promptCode: row?.promptCode || "",
    promptName: row?.promptName || "",
    sceneCode: row?.sceneCode || "",
    description: row?.description || "",
    status: row?.status || "ACTIVE"
  });
  templateDialogVisible.value = true;
}

async function saveTemplate() {
  await templateFormRef.value?.validate();
  saving.value = true;
  try {
    if (templateForm.id) {
      await updatePromptTemplate(templateForm.id, templateForm);
    } else {
      await createPromptTemplate(templateForm);
    }
    ElMessage.success("保存成功");
    templateDialogVisible.value = false;
    await loadTemplates();
  } finally {
    saving.value = false;
  }
}

async function removeTemplate(row: AiPromptTemplateInfo) {
  if (!row.id) {
    return;
  }
  await deletePromptTemplate(row.id);
  ElMessage.success("删除成功");
  if (selectedTemplate.value?.id === row.id) {
    selectedTemplate.value = null;
    versions.value = [];
  }
  await loadTemplates();
}

function openVersionDialog(row?: AiPromptVersionInfo) {
  if (!selectedTemplate.value?.id) {
    return;
  }
  Object.assign(versionForm, {
    id: row?.id,
    templateId: selectedTemplate.value.id,
    versionNo: row?.versionNo || "",
    title: row?.title || "",
    content: row?.content || "",
    variablesJson: row?.variablesJson || "",
    status: row?.status || "DRAFT",
    grayPercent: row?.grayPercent ?? 100,
    abGroup: row?.abGroup || ""
  });
  versionDialogVisible.value = true;
}

async function saveVersion() {
  await versionFormRef.value?.validate();
  saving.value = true;
  try {
    if (versionForm.id) {
      await updatePromptVersion(versionForm.id, versionForm);
    } else {
      await createPromptVersion(versionForm);
    }
    ElMessage.success("保存成功");
    versionDialogVisible.value = false;
    await loadVersions(versionForm.templateId);
  } finally {
    saving.value = false;
  }
}

async function publishVersion(row: AiPromptVersionInfo) {
  if (!row.id || !selectedTemplate.value?.id) {
    return;
  }
  await publishPromptVersion(row.id);
  ElMessage.success("发布成功");
  await loadVersions(selectedTemplate.value.id);
}

async function archiveVersion(row: AiPromptVersionInfo) {
  if (!row.id || !selectedTemplate.value?.id) {
    return;
  }
  await archivePromptVersion(row.id);
  ElMessage.success("归档成功");
  await loadVersions(selectedTemplate.value.id);
}

function versionTagType(status?: string) {
  if (status === "PUBLISHED") {
    return "success";
  }
  if (status === "ARCHIVED") {
    return "info";
  }
  return "warning";
}
</script>

<style scoped>
.prompt-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.page-header,
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.eyebrow {
  color: #409eff;
  font-size: 12px;
  font-weight: 700;
}

h1 {
  margin: 4px 0 0;
  font-size: 24px;
}

.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

.selected-template {
  margin-bottom: 12px;
  color: #606266;
}

.inline-fields {
  display: grid;
  grid-template-columns: 160px minmax(180px, 1fr);
  gap: 12px;
  width: 100%;
}
</style>
