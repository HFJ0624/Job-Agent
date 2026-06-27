<template>
  <main class="connector-page">
    <section class="connector-header">
      <div>
        <p class="eyebrow">External Connectors</p>
        <h1>外部连接器</h1>
      </div>
      <el-button :icon="Refresh" :loading="loading" @click="loadTools">刷新</el-button>
    </section>

    <section class="connector-grid">
      <article class="connector-panel">
        <div class="panel-title">
          <h2>连接器工具</h2>
          <el-tag effect="plain">{{ tools.length }} 个</el-tag>
        </div>

        <el-table
          v-loading="loading"
          :data="tools"
          border
          stripe
          highlight-current-row
          @row-click="selectTool"
        >
          <el-table-column prop="displayName" label="工具" min-width="170" />
          <el-table-column prop="toolName" label="工具名" min-width="280" show-overflow-tooltip />
          <el-table-column prop="sideEffectType" label="副作用" width="150">
            <template #default="{ row }">
              <el-tag :type="sideEffectTag(row.sideEffectType)" effect="plain">
                {{ row.sideEffectType }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="requiresUserConfirmation" label="确认" width="90">
            <template #default="{ row }">
              <el-tag :type="row.requiresUserConfirmation ? 'warning' : 'success'" effect="plain">
                {{ row.requiresUserConfirmation ? "需要" : "无需" }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="110" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" :icon="View" @click.stop="openDetail(row)">详情</el-button>
            </template>
          </el-table-column>
        </el-table>
      </article>

      <article class="connector-panel">
        <div class="panel-title">
          <h2>预览调用</h2>
          <el-tag v-if="currentTool" type="info" effect="plain">{{ currentTool.displayName }}</el-tag>
        </div>

        <el-empty v-if="!currentTool" description="请选择一个连接器工具" />

        <template v-else>
          <el-descriptions :column="1" border>
            <el-descriptions-item label="工具名">{{ currentTool.toolName }}</el-descriptions-item>
            <el-descriptions-item label="说明">{{ currentTool.description }}</el-descriptions-item>
            <el-descriptions-item label="确认策略">
              {{ currentTool.confirmationType || "-" }}
              <span v-if="currentTool.confirmationMessage">：{{ currentTool.confirmationMessage }}</span>
            </el-descriptions-item>
          </el-descriptions>

          <h3>入参 JSON</h3>
          <el-input
            v-model="previewJson"
            type="textarea"
            :rows="12"
            spellcheck="false"
            class="json-input"
          />

          <div class="action-row">
            <el-button @click="fillExampleParams">填充示例参数</el-button>
            <el-button type="primary" :loading="previewLoading" @click="runPreview">预览调用</el-button>
          </div>

          <h3>返回结果</h3>
          <pre class="json-box">{{ previewResult || "{}" }}</pre>
        </template>
      </article>
    </section>

    <el-drawer v-model="detailVisible" title="连接器工具详情" size="52%">
      <div v-if="detailTool" class="detail-content">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="显示名">{{ detailTool.displayName }}</el-descriptions-item>
          <el-descriptions-item label="版本">{{ detailTool.version || "-" }}</el-descriptions-item>
          <el-descriptions-item label="工具名" :span="2">{{ detailTool.toolName }}</el-descriptions-item>
          <el-descriptions-item label="Java 类">{{ detailTool.javaClassName }}</el-descriptions-item>
          <el-descriptions-item label="Java 方法">{{ detailTool.javaMethodName }}</el-descriptions-item>
          <el-descriptions-item label="权限">{{ detailTool.permissionType }}</el-descriptions-item>
          <el-descriptions-item label="副作用">{{ detailTool.sideEffectType }}</el-descriptions-item>
          <el-descriptions-item label="需要确认">{{ detailTool.requiresUserConfirmation ? "是" : "否" }}</el-descriptions-item>
          <el-descriptions-item label="确认策略">{{ detailTool.confirmationType }}</el-descriptions-item>
          <el-descriptions-item label="说明" :span="2">{{ detailTool.description }}</el-descriptions-item>
        </el-descriptions>

        <h3>入参 Schema</h3>
        <el-table :data="detailTool.inputParams || []" border stripe>
          <el-table-column prop="name" label="参数" width="160" />
          <el-table-column prop="type" label="类型" width="100" />
          <el-table-column prop="required" label="必填" width="80">
            <template #default="{ row }">{{ row.required ? "是" : "否" }}</template>
          </el-table-column>
          <el-table-column prop="description" label="说明" min-width="220" />
          <el-table-column prop="example" label="示例" min-width="160" />
        </el-table>

        <h3>出参 Schema</h3>
        <el-table :data="detailTool.outputFields || []" border stripe>
          <el-table-column prop="name" label="字段" width="180" />
          <el-table-column prop="type" label="类型" width="100" />
          <el-table-column prop="nullable" label="可空" width="80">
            <template #default="{ row }">{{ row.nullable ? "是" : "否" }}</template>
          </el-table-column>
          <el-table-column prop="description" label="说明" min-width="220" />
        </el-table>

        <h3>错误码</h3>
        <el-table :data="detailTool.errorCodes || []" border stripe>
          <el-table-column prop="code" label="错误码" width="220" />
          <el-table-column prop="message" label="说明" min-width="220" />
          <el-table-column prop="retryable" label="可重试" width="90">
            <template #default="{ row }">{{ row.retryable ? "是" : "否" }}</template>
          </el-table-column>
        </el-table>
      </div>
    </el-drawer>
  </main>
</template>

<script setup lang="ts">
import { onMounted, ref } from "vue";
import { ElMessage } from "element-plus";
import { Refresh, View } from "@element-plus/icons-vue";
import {
  getExternalConnectorTool,
  listExternalConnectorTools,
  previewExternalConnectorTool
} from "../../api/externalConnector";
import type { AgentToolParamSchemaInfo, AgentToolSchemaInfo } from "../../api/types";

const loading = ref(false);
const previewLoading = ref(false);
const detailVisible = ref(false);
const tools = ref<AgentToolSchemaInfo[]>([]);
const currentTool = ref<AgentToolSchemaInfo | null>(null);
const detailTool = ref<AgentToolSchemaInfo | null>(null);
const previewJson = ref("{}");
const previewResult = ref("");

onMounted(() => {
  loadTools();
});

async function loadTools() {
  loading.value = true;
  try {
    tools.value = await listExternalConnectorTools();
    if (!currentTool.value && tools.value.length > 0) {
      selectTool(tools.value[0]);
    }
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : "外部连接器加载失败");
  } finally {
    loading.value = false;
  }
}

function selectTool(row: AgentToolSchemaInfo) {
  currentTool.value = row;
  previewResult.value = "";
  fillExampleParams();
}

async function openDetail(row: AgentToolSchemaInfo) {
  detailVisible.value = true;
  detailTool.value = row;
  try {
    detailTool.value = await getExternalConnectorTool(row.toolName);
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : "连接器详情加载失败");
  }
}

function fillExampleParams() {
  if (!currentTool.value) {
    previewJson.value = "{}";
    return;
  }
  const params: Record<string, unknown> = {};
  (currentTool.value.inputParams || []).forEach((param: AgentToolParamSchemaInfo) => {
    params[param.name] = exampleValue(param);
  });
  previewJson.value = JSON.stringify(params, null, 2);
}

async function runPreview() {
  if (!currentTool.value) {
    ElMessage.warning("请选择连接器工具");
    return;
  }

  let params: Record<string, unknown>;
  try {
    params = JSON.parse(previewJson.value || "{}");
  } catch {
    ElMessage.error("入参 JSON 格式不正确");
    return;
  }

  previewLoading.value = true;
  try {
    const result = await previewExternalConnectorTool({
      toolName: currentTool.value.toolName,
      params
    });
    previewResult.value = formatJson(result);
    ElMessage.success("预览调用完成");
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : "预览调用失败");
  } finally {
    previewLoading.value = false;
  }
}

function exampleValue(param: AgentToolParamSchemaInfo) {
  const raw = param.example ?? param.defaultValue;
  if (param.type === "INTEGER" || param.type === "LONG") {
    const value = Number(raw || 0);
    return Number.isFinite(value) ? value : 0;
  }
  if (param.type === "BOOLEAN") {
    return raw === "true";
  }
  return raw || "";
}

function formatJson(value: unknown) {
  if (!value) {
    return "{}";
  }
  if (typeof value === "string") {
    try {
      return JSON.stringify(JSON.parse(value), null, 2);
    } catch {
      return value;
    }
  }
  return JSON.stringify(value, null, 2);
}

function sideEffectTag(value?: string) {
  if (value === "READ_ONLY") {
    return "success";
  }
  if (value === "EXTERNAL_ACTION") {
    return "danger";
  }
  return "warning";
}
</script>

<style scoped>
.connector-page {
  display: grid;
  gap: 16px;
}

.connector-header,
.connector-panel {
  padding: 20px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #ffffff;
}

.connector-header,
.panel-title,
.action-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.connector-header h1,
.panel-title h2 {
  margin: 0;
  color: #111827;
}

.eyebrow {
  margin: 0 0 4px;
  color: #2563eb;
  font-size: 13px;
  font-weight: 700;
}

.connector-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.25fr) minmax(360px, 0.75fr);
  gap: 16px;
}

.connector-panel {
  min-width: 0;
}

.connector-panel h3,
.detail-content h3 {
  margin: 18px 0 10px;
  color: #111827;
  font-size: 15px;
}

.json-input {
  font-family: Consolas, Monaco, monospace;
}

.action-row {
  justify-content: flex-end;
  margin-top: 12px;
}

.json-box {
  min-height: 180px;
  max-height: 420px;
  overflow: auto;
  padding: 14px;
  border-radius: 8px;
  background: #0f172a;
  color: #e5e7eb;
  font-size: 13px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
}

.detail-content {
  display: grid;
  gap: 12px;
}

@media (max-width: 1180px) {
  .connector-grid {
    grid-template-columns: 1fr;
  }
}
</style>
