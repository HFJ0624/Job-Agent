<template>
  <div class="model-page">
    <div class="page-header">
      <div>
        <div class="eyebrow">Model Gateway</div>
        <h1>模型与路由管理</h1>
      </div>
      <el-button type="primary" @click="openModelDialog()">新增模型</el-button>
    </div>

    <el-row :gutter="12">
      <el-col :span="6">
        <el-card shadow="never">
          <div class="stat-label">总调用</div>
          <div class="stat-value">{{ stats.totalCalls }}</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never">
          <div class="stat-label">成功 / 失败</div>
          <div class="stat-value">{{ stats.successCalls }} / {{ stats.failedCalls }}</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never">
          <div class="stat-label">总 Token</div>
          <div class="stat-value">{{ stats.totalTokens }}</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never">
          <div class="stat-label">总成本</div>
          <div class="stat-value">{{ stats.totalCost }}</div>
        </el-card>
      </el-col>
    </el-row>

    <el-tabs v-model="activeTab" type="border-card">
      <el-tab-pane label="模型配置" name="models">
        <el-form :model="modelQuery" inline>
          <el-form-item label="模型编码">
            <el-input v-model="modelQuery.modelCode" clearable placeholder="如 OPENAI_MAIN" />
          </el-form-item>
          <el-form-item label="供应商">
            <el-input v-model="modelQuery.provider" clearable placeholder="OPENAI / DEEPSEEK" />
          </el-form-item>
          <el-form-item label="状态">
            <el-select v-model="modelQuery.status" clearable placeholder="全部" style="width: 140px">
              <el-option label="启用" value="ACTIVE" />
              <el-option label="停用" value="DISABLED" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="loadModels">查询</el-button>
            <el-button @click="resetModelQuery">重置</el-button>
          </el-form-item>
        </el-form>

        <el-table v-loading="modelLoading" border :data="models">
          <el-table-column prop="id" label="ID" width="80" />
          <el-table-column prop="modelCode" label="模型编码" min-width="160" />
          <el-table-column prop="modelName" label="名称" min-width="160" />
          <el-table-column prop="provider" label="供应商" width="120" />
          <el-table-column prop="modelIdentifier" label="模型标识" min-width="180" />
          <el-table-column prop="timeoutSeconds" label="超时" width="90" />
          <el-table-column prop="maxRetries" label="重试" width="90" />
          <el-table-column prop="apiKey" label="API Key" min-width="160" />
          <el-table-column prop="status" label="状态" width="110">
            <template #default="{ row }">
              <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'">{{ row.status }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="160" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="openModelDialog(row)">编辑</el-button>
              <el-popconfirm title="确认删除这个模型配置？" @confirm="removeModel(row)">
                <template #reference>
                  <el-button link type="danger">删除</el-button>
                </template>
              </el-popconfirm>
            </template>
          </el-table-column>
        </el-table>

        <div class="pager">
          <el-pagination
            v-model:current-page="modelQuery.pageNum"
            v-model:page-size="modelQuery.pageSize"
            background
            layout="total, sizes, prev, pager, next"
            :total="modelTotal"
            @size-change="loadModels"
            @current-change="loadModels"
          />
        </div>
      </el-tab-pane>

      <el-tab-pane label="模型路由" name="routes">
        <div class="toolbar">
          <el-form :model="routeQuery" inline>
            <el-form-item label="业务场景">
              <el-input v-model="routeQuery.sceneCode" clearable placeholder="AGENT_SUMMARY" />
            </el-form-item>
            <el-form-item label="Prompt">
              <el-input v-model="routeQuery.promptCode" clearable placeholder="Prompt 编码" />
            </el-form-item>
            <el-form-item label="状态">
              <el-select v-model="routeQuery.status" clearable placeholder="全部" style="width: 140px">
                <el-option label="启用" value="ACTIVE" />
                <el-option label="停用" value="DISABLED" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="loadRoutes">查询</el-button>
              <el-button @click="resetRouteQuery">重置</el-button>
            </el-form-item>
          </el-form>
          <el-button type="primary" @click="openRouteDialog()">新增路由</el-button>
        </div>

        <el-table v-loading="routeLoading" border :data="routes">
          <el-table-column prop="id" label="ID" width="80" />
          <el-table-column prop="sceneCode" label="业务场景" min-width="160" />
          <el-table-column prop="routeName" label="路由名称" min-width="160" />
          <el-table-column prop="primaryModelCode" label="主模型" min-width="160" />
          <el-table-column prop="fallbackModelCode" label="备用模型" min-width="160" />
          <el-table-column prop="promptCode" label="Prompt" min-width="160" />
          <el-table-column prop="promptVersionId" label="固定版本ID" width="120" />
          <el-table-column prop="grayPercent" label="灰度" width="90">
            <template #default="{ row }">{{ row.grayPercent ?? 100 }}%</template>
          </el-table-column>
          <el-table-column prop="abGroup" label="A/B" width="90" />
          <el-table-column prop="status" label="状态" width="110">
            <template #default="{ row }">
              <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'">{{ row.status }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="160" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="openRouteDialog(row)">编辑</el-button>
              <el-popconfirm title="确认删除这个模型路由？" @confirm="removeRoute(row)">
                <template #reference>
                  <el-button link type="danger">删除</el-button>
                </template>
              </el-popconfirm>
            </template>
          </el-table-column>
        </el-table>

        <div class="pager">
          <el-pagination
            v-model:current-page="routeQuery.pageNum"
            v-model:page-size="routeQuery.pageSize"
            background
            layout="total, sizes, prev, pager, next"
            :total="routeTotal"
            @size-change="loadRoutes"
            @current-change="loadRoutes"
          />
        </div>
      </el-tab-pane>

      <el-tab-pane label="调用日志" name="logs">
        <el-form :model="logQuery" inline>
          <el-form-item label="TraceId">
            <el-input v-model="logQuery.traceId" clearable />
          </el-form-item>
          <el-form-item label="场景">
            <el-input v-model="logQuery.sceneCode" clearable />
          </el-form-item>
          <el-form-item label="模型">
            <el-input v-model="logQuery.modelCode" clearable />
          </el-form-item>
          <el-form-item label="状态">
            <el-select v-model="logQuery.status" clearable placeholder="全部" style="width: 140px">
              <el-option label="成功" value="SUCCESS" />
              <el-option label="失败" value="FAILED" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="loadLogs">查询</el-button>
            <el-button @click="resetLogQuery">重置</el-button>
          </el-form-item>
        </el-form>

        <el-table v-loading="logLoading" border :data="logs">
          <el-table-column prop="id" label="ID" width="80" />
          <el-table-column prop="traceId" label="TraceId" min-width="220" show-overflow-tooltip />
          <el-table-column prop="userId" label="用户ID" width="100" />
          <el-table-column prop="sceneCode" label="场景" min-width="150" />
          <el-table-column prop="modelCode" label="模型" min-width="150" />
          <el-table-column prop="fallbackUsed" label="降级" width="80">
            <template #default="{ row }">{{ row.fallbackUsed === 1 ? "是" : "否" }}</template>
          </el-table-column>
          <el-table-column prop="totalTokens" label="Token" width="100" />
          <el-table-column prop="totalCost" label="成本" width="110" />
          <el-table-column prop="costTime" label="耗时(ms)" width="110" />
          <el-table-column prop="status" label="状态" width="110">
            <template #default="{ row }">
              <el-tag :type="row.status === 'SUCCESS' ? 'success' : 'danger'">{{ row.status }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="errorMsg" label="错误" min-width="220" show-overflow-tooltip />
          <el-table-column prop="createTime" label="时间" width="180" />
        </el-table>

        <div class="pager">
          <el-pagination
            v-model:current-page="logQuery.pageNum"
            v-model:page-size="logQuery.pageSize"
            background
            layout="total, sizes, prev, pager, next"
            :total="logTotal"
            @size-change="loadLogs"
            @current-change="loadLogs"
          />
        </div>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="modelDialogVisible" :title="modelForm.id ? '编辑模型' : '新增模型'" width="760px">
      <el-form ref="modelFormRef" :model="modelForm" :rules="modelRules" label-width="120px">
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="模型编码" prop="modelCode">
              <el-input v-model="modelForm.modelCode" placeholder="OPENAI_MAIN" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="模型名称" prop="modelName">
              <el-input v-model="modelForm.modelName" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="供应商" prop="provider">
              <el-input v-model="modelForm.provider" placeholder="OPENAI" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="模型标识" prop="modelIdentifier">
              <el-input v-model="modelForm.modelIdentifier" placeholder="gpt-4.1-mini" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="BaseUrl" prop="baseUrl">
          <el-input v-model="modelForm.baseUrl" placeholder="https://api.openai.com/v1" />
        </el-form-item>
        <el-form-item label="API Key">
          <el-input v-model="modelForm.apiKey" show-password placeholder="留空或保留 ****** 表示不修改" />
        </el-form-item>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="接口路径">
              <el-input v-model="modelForm.chatPath" placeholder="/chat/completions" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="状态">
              <el-radio-group v-model="modelForm.status">
                <el-radio-button label="ACTIVE">启用</el-radio-button>
                <el-radio-button label="DISABLED">停用</el-radio-button>
              </el-radio-group>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="8">
            <el-form-item label="温度">
              <el-input-number v-model="modelForm.temperature" :min="0" :max="2" :step="0.1" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="最大Token">
              <el-input-number v-model="modelForm.maxTokens" :min="1" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="超时秒">
              <el-input-number v-model="modelForm.timeoutSeconds" :min="1" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="8">
            <el-form-item label="重试次数">
              <el-input-number v-model="modelForm.maxRetries" :min="0" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="输入单价/1k">
              <el-input-number v-model="modelForm.inputPricePer1k" :min="0" :precision="6" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="输出单价/1k">
              <el-input-number v-model="modelForm.outputPricePer1k" :min="0" :precision="6" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="8">
            <el-form-item label="启用熔断">
              <el-switch v-model="modelForm.circuitEnabled" :active-value="1" :inactive-value="0" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="失败阈值">
              <el-input-number v-model="modelForm.failureThreshold" :min="1" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="冷却秒">
              <el-input-number v-model="modelForm.cooldownSeconds" :min="1" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="备注">
          <el-input v-model="modelForm.remark" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="modelDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveModel">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="routeDialogVisible" :title="routeForm.id ? '编辑路由' : '新增路由'" width="680px">
      <el-form ref="routeFormRef" :model="routeForm" :rules="routeRules" label-width="120px">
        <el-form-item label="业务场景" prop="sceneCode">
          <el-input v-model="routeForm.sceneCode" placeholder="AGENT_SUMMARY" />
        </el-form-item>
        <el-form-item label="路由名称" prop="routeName">
          <el-input v-model="routeForm.routeName" />
        </el-form-item>
        <el-form-item label="主模型" prop="primaryModelCode">
          <el-select v-model="routeForm.primaryModelCode" filterable placeholder="选择主模型">
            <el-option v-for="item in activeModels" :key="item.modelCode" :label="item.modelName" :value="item.modelCode" />
          </el-select>
        </el-form-item>
        <el-form-item label="备用模型">
          <el-select v-model="routeForm.fallbackModelCode" clearable filterable placeholder="可选">
            <el-option v-for="item in activeModels" :key="item.modelCode" :label="item.modelName" :value="item.modelCode" />
          </el-select>
        </el-form-item>
        <el-form-item label="Prompt 编码" prop="promptCode">
          <el-input v-model="routeForm.promptCode" placeholder="AGENT_SUMMARY" />
        </el-form-item>
        <el-form-item label="固定版本ID">
          <el-input-number v-model="routeForm.promptVersionId" :min="1" clearable />
        </el-form-item>
        <el-form-item label="灰度 / A-B">
          <div class="inline-fields">
            <el-input-number v-model="routeForm.grayPercent" :min="0" :max="100" />
            <el-input v-model="routeForm.abGroup" clearable placeholder="A/B 分组，可空" />
          </div>
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="routeForm.status">
            <el-radio-button label="ACTIVE">启用</el-radio-button>
            <el-radio-button label="DISABLED">停用</el-radio-button>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="routeDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveRoute">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import { ElMessage, type FormInstance, type FormRules } from "element-plus";
import {
  createModelConfig,
  createModelRoute,
  deleteModelConfig,
  deleteModelRoute,
  getModelCostStats,
  listActiveModelConfigs,
  pageModelCallLogs,
  pageModelConfigs,
  pageModelRoutes,
  updateModelConfig,
  updateModelRoute
} from "../../api/aiModel";
import type {
  AiModelCallLogInfo,
  AiModelConfigInfo,
  AiModelCostStats,
  AiModelRouteInfo
} from "../../api/types";

const activeTab = ref("models");
const modelLoading = ref(false);
const routeLoading = ref(false);
const logLoading = ref(false);
const saving = ref(false);
const modelDialogVisible = ref(false);
const routeDialogVisible = ref(false);
const models = ref<AiModelConfigInfo[]>([]);
const activeModels = ref<AiModelConfigInfo[]>([]);
const routes = ref<AiModelRouteInfo[]>([]);
const logs = ref<AiModelCallLogInfo[]>([]);
const modelTotal = ref(0);
const routeTotal = ref(0);
const logTotal = ref(0);
const modelFormRef = ref<FormInstance>();
const routeFormRef = ref<FormInstance>();

const stats = reactive<AiModelCostStats>({
  totalCalls: 0,
  successCalls: 0,
  failedCalls: 0,
  totalTokens: 0,
  totalCost: 0,
  avgCostTime: 0
});

const modelQuery = reactive({
  pageNum: 1,
  pageSize: 10,
  modelCode: "",
  modelName: "",
  provider: "",
  status: ""
});

const routeQuery = reactive({
  pageNum: 1,
  pageSize: 10,
  sceneCode: "",
  promptCode: "",
  primaryModelCode: "",
  status: ""
});

const logQuery = reactive({
  pageNum: 1,
  pageSize: 10,
  traceId: "",
  userId: "",
  sceneCode: "",
  modelCode: "",
  status: "",
  startTime: "",
  endTime: ""
});

const modelForm = reactive<AiModelConfigInfo>({
  modelCode: "",
  modelName: "",
  provider: "OPENAI",
  baseUrl: "",
  apiKey: "",
  chatPath: "/chat/completions",
  modelIdentifier: "",
  temperature: 0.2,
  maxTokens: 1200,
  timeoutSeconds: 45,
  maxRetries: 0,
  inputPricePer1k: 0,
  outputPricePer1k: 0,
  circuitEnabled: 1,
  failureThreshold: 3,
  cooldownSeconds: 60,
  status: "ACTIVE",
  remark: ""
});

const routeForm = reactive<AiModelRouteInfo>({
  sceneCode: "",
  routeName: "",
  primaryModelCode: "",
  fallbackModelCode: "",
  promptCode: "",
  promptVersionId: null,
  grayPercent: 100,
  abGroup: "",
  status: "ACTIVE"
});

const modelRules: FormRules = {
  modelCode: [{ required: true, message: "请输入模型编码", trigger: "blur" }],
  modelName: [{ required: true, message: "请输入模型名称", trigger: "blur" }],
  provider: [{ required: true, message: "请输入供应商", trigger: "blur" }],
  baseUrl: [{ required: true, message: "请输入 BaseUrl", trigger: "blur" }],
  modelIdentifier: [{ required: true, message: "请输入模型标识", trigger: "blur" }]
};

const routeRules: FormRules = {
  sceneCode: [{ required: true, message: "请输入业务场景", trigger: "blur" }],
  routeName: [{ required: true, message: "请输入路由名称", trigger: "blur" }],
  primaryModelCode: [{ required: true, message: "请选择主模型", trigger: "change" }],
  promptCode: [{ required: true, message: "请输入 Prompt 编码", trigger: "blur" }]
};

onMounted(async () => {
  await Promise.all([loadModels(), loadRoutes(), loadLogs(), loadActiveModels()]);
});

async function loadModels() {
  modelLoading.value = true;
  try {
    const result = await pageModelConfigs(modelQuery);
    models.value = result.records || [];
    modelTotal.value = result.total || 0;
  } finally {
    modelLoading.value = false;
  }
}

async function loadActiveModels() {
  activeModels.value = await listActiveModelConfigs();
}

function resetModelQuery() {
  modelQuery.pageNum = 1;
  modelQuery.modelCode = "";
  modelQuery.modelName = "";
  modelQuery.provider = "";
  modelQuery.status = "";
  loadModels();
}

async function loadRoutes() {
  routeLoading.value = true;
  try {
    const result = await pageModelRoutes(routeQuery);
    routes.value = result.records || [];
    routeTotal.value = result.total || 0;
  } finally {
    routeLoading.value = false;
  }
}

function resetRouteQuery() {
  routeQuery.pageNum = 1;
  routeQuery.sceneCode = "";
  routeQuery.promptCode = "";
  routeQuery.primaryModelCode = "";
  routeQuery.status = "";
  loadRoutes();
}

async function loadLogs() {
  logLoading.value = true;
  try {
    const [pageResult, statsResult] = await Promise.all([
      pageModelCallLogs(logQuery),
      getModelCostStats(logQuery)
    ]);
    logs.value = pageResult.records || [];
    logTotal.value = pageResult.total || 0;
    Object.assign(stats, statsResult);
  } finally {
    logLoading.value = false;
  }
}

function resetLogQuery() {
  logQuery.pageNum = 1;
  logQuery.traceId = "";
  logQuery.userId = "";
  logQuery.sceneCode = "";
  logQuery.modelCode = "";
  logQuery.status = "";
  logQuery.startTime = "";
  logQuery.endTime = "";
  loadLogs();
}

function openModelDialog(row?: AiModelConfigInfo) {
  Object.assign(modelForm, {
    id: row?.id,
    modelCode: row?.modelCode || "",
    modelName: row?.modelName || "",
    provider: row?.provider || "OPENAI",
    baseUrl: row?.baseUrl || "",
    apiKey: row?.apiKey || "",
    chatPath: row?.chatPath || "/chat/completions",
    modelIdentifier: row?.modelIdentifier || "",
    temperature: row?.temperature ?? 0.2,
    maxTokens: row?.maxTokens ?? 1200,
    timeoutSeconds: row?.timeoutSeconds ?? 45,
    maxRetries: row?.maxRetries ?? 0,
    inputPricePer1k: row?.inputPricePer1k ?? 0,
    outputPricePer1k: row?.outputPricePer1k ?? 0,
    circuitEnabled: row?.circuitEnabled ?? 1,
    failureThreshold: row?.failureThreshold ?? 3,
    cooldownSeconds: row?.cooldownSeconds ?? 60,
    status: row?.status || "ACTIVE",
    remark: row?.remark || ""
  });
  modelDialogVisible.value = true;
}

async function saveModel() {
  await modelFormRef.value?.validate();
  saving.value = true;
  try {
    if (modelForm.id) {
      await updateModelConfig(modelForm.id, modelForm);
    } else {
      await createModelConfig(modelForm);
    }
    ElMessage.success("保存成功");
    modelDialogVisible.value = false;
    await Promise.all([loadModels(), loadActiveModels()]);
  } finally {
    saving.value = false;
  }
}

async function removeModel(row: AiModelConfigInfo) {
  if (!row.id) {
    return;
  }
  await deleteModelConfig(row.id);
  ElMessage.success("删除成功");
  await Promise.all([loadModels(), loadActiveModels()]);
}

function openRouteDialog(row?: AiModelRouteInfo) {
  Object.assign(routeForm, {
    id: row?.id,
    sceneCode: row?.sceneCode || "",
    routeName: row?.routeName || "",
    primaryModelCode: row?.primaryModelCode || "",
    fallbackModelCode: row?.fallbackModelCode || "",
    promptCode: row?.promptCode || "",
    promptVersionId: row?.promptVersionId || null,
    grayPercent: row?.grayPercent ?? 100,
    abGroup: row?.abGroup || "",
    status: row?.status || "ACTIVE"
  });
  routeDialogVisible.value = true;
}

async function saveRoute() {
  await routeFormRef.value?.validate();
  saving.value = true;
  try {
    if (routeForm.id) {
      await updateModelRoute(routeForm.id, routeForm);
    } else {
      await createModelRoute(routeForm);
    }
    ElMessage.success("保存成功");
    routeDialogVisible.value = false;
    await loadRoutes();
  } finally {
    saving.value = false;
  }
}

async function removeRoute(row: AiModelRouteInfo) {
  if (!row.id) {
    return;
  }
  await deleteModelRoute(row.id);
  ElMessage.success("删除成功");
  await loadRoutes();
}
</script>

<style scoped>
.model-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.page-header,
.toolbar {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
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

.stat-label {
  color: #909399;
  font-size: 13px;
}

.stat-value {
  margin-top: 8px;
  font-size: 24px;
  font-weight: 700;
  color: #1f2d3d;
}

.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

.inline-fields {
  display: grid;
  grid-template-columns: 160px minmax(180px, 1fr);
  gap: 12px;
  width: 100%;
}
</style>
