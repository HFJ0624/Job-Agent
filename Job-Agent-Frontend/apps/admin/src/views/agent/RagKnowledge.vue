<template>
  <main class="rag-page">
    <section class="rag-header">
      <div>
        <p class="eyebrow">RAG Knowledge Base</p>
        <h1>RAG 知识库</h1>
      </div>

      <el-button :icon="Refresh" :loading="statsLoading" @click="loadStats">
        刷新
      </el-button>
    </section>

    <section class="rag-metrics">
      <div class="metric-panel">
        <span>向量表</span>
        <strong>{{ stats?.tableName || "-" }}</strong>
      </div>
      <div class="metric-panel">
        <span>向量维度</span>
        <strong>{{ stats?.dimension || "-" }}</strong>
      </div>
      <div class="metric-panel">
        <span>总分片</span>
        <strong>{{ stats?.totalChunks || 0 }}</strong>
      </div>
      <div class="metric-panel">
        <span>公共 / 私有</span>
        <strong>{{ stats?.publicChunks || 0 }} / {{ stats?.privateChunks || 0 }}</strong>
      </div>
    </section>

    <el-alert
      v-if="stats && !stats.schemaReady"
      class="rag-status-alert"
      type="warning"
      show-icon
      :closable="false"
      title="RAG 知识库未完成初始化"
      :description="stats.setupMessage || '请检查 pgvector 扩展、向量表和数据库权限配置。'"
    />

    <section class="rag-panel">
      <div class="panel-title">
        <h2>索引管理</h2>
        <el-tag type="info" effect="plain">
          chunk {{ stats?.chunkSize || "-" }} / overlap {{ stats?.chunkOverlap || "-" }}
        </el-tag>
      </div>

      <div class="index-actions">
        <el-button
          type="primary"
          :disabled="stats?.extensionReady === false"
          :loading="indexLoading.all"
          @click="runRebuildAll"
        >
          重建全部
        </el-button>
        <el-button
          type="success"
          :disabled="stats?.extensionReady === false"
          :loading="indexLoading.public"
          @click="runRebuildPublic"
        >
          重建公共知识
        </el-button>
        <el-input-number
          v-model="targetUserId"
          :min="1"
          :step="1"
          controls-position="right"
          placeholder="用户ID"
        />
        <el-button
          type="warning"
          :disabled="stats?.extensionReady === false"
          :loading="indexLoading.user"
          @click="runRebuildUser"
        >
          重建用户知识
        </el-button>
      </div>

      <el-alert
        v-if="lastIndexResult"
        class="index-result"
        type="success"
        show-icon
        :closable="false"
      >
        <template #title>
          文档 {{ lastIndexResult.indexedDocumentCount }} 个，分片 {{ lastIndexResult.indexedChunkCount }} 个，跳过 {{ lastIndexResult.skippedDocumentCount }} 个
        </template>
      </el-alert>
    </section>

    <section class="rag-panel">
      <div class="panel-title">
        <h2>索引分布</h2>
        <el-tag type="success" effect="plain">
          minScore {{ stats?.minScore ?? "-" }}
        </el-tag>
      </div>

      <el-table
        v-loading="statsLoading"
        :data="stats?.typeStats || []"
        border
        stripe
      >
        <el-table-column prop="userId" label="用户ID" width="110">
          <template #default="{ row }">
            <el-tag :type="row.userId === 0 ? 'success' : 'warning'" effect="plain">
              {{ row.userId === 0 ? "公共" : row.userId }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="documentType" label="文档类型" width="210" />
        <el-table-column prop="documentCount" label="业务文档" width="120" />
        <el-table-column prop="chunkCount" label="分片数" width="120" />
      </el-table>
    </section>

    <section class="rag-panel">
      <div class="panel-title">
        <h2>检索预览</h2>
        <el-tag effect="plain">
          默认召回 {{ stats?.maxResults || "-" }}
        </el-tag>
      </div>

      <div class="search-row">
        <el-input-number
          v-model="searchForm.userId"
          :min="0"
          :step="1"
          controls-position="right"
        />
        <el-input
          v-model.trim="searchForm.query"
          clearable
          placeholder="输入要检索的问题"
          @keyup.enter="runSearch"
        />
        <el-input-number
          v-model="searchForm.limit"
          :min="1"
          :max="20"
          controls-position="right"
        />
        <el-button
          type="primary"
          :icon="Search"
          :disabled="stats?.extensionReady === false"
          :loading="searchLoading"
          @click="runSearch"
        >
          检索
        </el-button>
      </div>

      <el-table
        v-loading="searchLoading"
        :data="searchResults"
        border
        stripe
      >
        <el-table-column prop="score" label="得分" width="100">
          <template #default="{ row }">
            {{ row.score.toFixed(4) }}
          </template>
        </el-table-column>
        <el-table-column prop="documentType" label="类型" width="180" />
        <el-table-column prop="title" label="标题" min-width="220" />
        <el-table-column prop="content" label="召回内容" min-width="360">
          <template #default="{ row }">
            <span class="content-preview">{{ row.content }}</span>
          </template>
        </el-table-column>
        <el-table-column label="元数据" width="120">
          <template #default="{ row }">
            <el-button link type="primary" @click="openMetadata(row.metadata)">
              查看
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <el-dialog v-model="metadataVisible" title="RAG 元数据" width="680px">
      <pre class="json-box">{{ metadataText }}</pre>
    </el-dialog>
  </main>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import { ElMessage } from "element-plus";
import { Refresh, Search } from "@element-plus/icons-vue";
import {
  getRagStats,
  rebuildAllRagKnowledge,
  rebuildPublicRagKnowledge,
  rebuildUserRagKnowledge,
  searchRagKnowledge
} from "../../api/rag";
import type {
  RagIndexResult,
  RagSearchResult,
  RagStats
} from "../../api/types";

/**
 * RAG 统计信息。
 */
const stats = ref<RagStats | null>(null);

/**
 * 最近一次索引结果。
 */
const lastIndexResult = ref<RagIndexResult | null>(null);

/**
 * 检索结果。
 */
const searchResults = ref<RagSearchResult[]>([]);

const statsLoading = ref(false);
const searchLoading = ref(false);
const targetUserId = ref<number | null>(null);

const indexLoading = reactive({
  all: false,
  public: false,
  user: false
});

const searchForm = reactive({
  userId: 0,
  query: "",
  limit: 5
});

const metadataVisible = ref(false);
const metadataText = ref("");

onMounted(() => {
  loadStats();
});

/**
 * 加载 RAG 统计信息。
 */
async function loadStats() {
  statsLoading.value = true;

  try {
    stats.value = await getRagStats();
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : "RAG 统计加载失败");
  } finally {
    statsLoading.value = false;
  }
}

/**
 * 重建全部知识。
 */
async function runRebuildAll() {
  await runIndexTask("all", rebuildAllRagKnowledge);
}

/**
 * 重建公共知识。
 */
async function runRebuildPublic() {
  await runIndexTask("public", rebuildPublicRagKnowledge);
}

/**
 * 重建指定用户私有知识。
 */
async function runRebuildUser() {
  if (!targetUserId.value) {
    ElMessage.warning("请输入用户ID");
    return;
  }

  await runIndexTask("user", () => rebuildUserRagKnowledge(Number(targetUserId.value)));
}

/**
 * 执行索引任务并刷新统计。
 *
 * @param key loading 标识
 * @param task 具体索引任务
 */
async function runIndexTask(
  key: keyof typeof indexLoading,
  task: () => Promise<RagIndexResult>
) {
  indexLoading[key] = true;

  try {
    lastIndexResult.value = await task();
    ElMessage.success("RAG 索引完成");
    await loadStats();
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : "RAG 索引失败");
  } finally {
    indexLoading[key] = false;
  }
}

/**
 * 执行检索预览。
 */
async function runSearch() {
  if (!searchForm.query) {
    ElMessage.warning("请输入检索问题");
    return;
  }

  searchLoading.value = true;

  try {
    searchResults.value = await searchRagKnowledge(
      Number(searchForm.userId || 0),
      searchForm.query,
      searchForm.limit
    );
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : "RAG 检索失败");
  } finally {
    searchLoading.value = false;
  }
}

/**
 * 打开元数据弹窗。
 *
 * @param metadata RAG 元数据
 */
function openMetadata(metadata?: Record<string, unknown>) {
  metadataText.value = JSON.stringify(metadata || {}, null, 2);
  metadataVisible.value = true;
}
</script>

<style scoped>
.rag-page {
  display: grid;
  gap: 16px;
}

.rag-header,
.rag-panel {
  padding: 20px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #ffffff;
}

.rag-header,
.panel-title,
.index-actions,
.search-row {
  display: flex;
  align-items: center;
  gap: 12px;
}

.rag-header,
.panel-title {
  justify-content: space-between;
}

.rag-header h1,
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

.rag-metrics {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
}

.metric-panel {
  display: grid;
  gap: 8px;
  min-height: 92px;
  padding: 18px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #ffffff;
}

.metric-panel span {
  color: #6b7280;
  font-size: 13px;
}

.metric-panel strong {
  color: #111827;
  font-size: 24px;
  word-break: break-all;
}

.index-actions,
.search-row {
  margin-top: 14px;
  flex-wrap: wrap;
}

.index-result {
  margin-top: 14px;
}

.rag-status-alert {
  border-radius: 8px;
}

.search-row .el-input {
  flex: 1;
  min-width: 260px;
}

.content-preview {
  display: -webkit-box;
  overflow: hidden;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 3;
  line-height: 1.6;
}

.json-box {
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

@media (max-width: 980px) {
  .rag-header,
  .panel-title,
  .index-actions,
  .search-row {
    align-items: stretch;
    flex-direction: column;
  }

  .rag-metrics {
    grid-template-columns: 1fr;
  }

  .search-row .el-input,
  .search-row .el-input-number,
  .index-actions .el-input-number {
    width: 100%;
  }
}
</style>
