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

    <el-tabs v-model="activeTab" class="rag-tabs">
      <el-tab-pane label="概览与索引" name="overview">
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
      </el-tab-pane>

      <el-tab-pane label="文档列表" name="documents">
        <section class="rag-panel">
          <div class="panel-title">
            <h2>RAG 文档</h2>
            <el-button :icon="Refresh" :loading="documentLoading" @click="loadDocuments">
              刷新文档
            </el-button>
          </div>

          <el-form class="filter-form" :model="documentQuery" label-width="88px">
            <el-form-item label="用户ID">
              <el-input v-model.trim="documentQuery.userId" clearable placeholder="0 为公共知识" />
            </el-form-item>
            <el-form-item label="文档类型">
              <el-select v-model="documentQuery.documentType" clearable placeholder="全部">
                <el-option
                  v-for="item in documentTypeOptions"
                  :key="item"
                  :label="item"
                  :value="item"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="业务ID">
              <el-input v-model.trim="documentQuery.businessId" clearable placeholder="来源业务ID" />
            </el-form-item>
            <el-form-item label="权限">
              <el-select v-model="documentQuery.permissionScope" clearable placeholder="全部">
                <el-option label="PUBLIC" value="PUBLIC" />
                <el-option label="PRIVATE" value="PRIVATE" />
              </el-select>
            </el-form-item>
            <el-form-item label="状态">
              <el-select v-model="documentQuery.status" clearable placeholder="全部">
                <el-option label="ACTIVE" value="ACTIVE" />
                <el-option label="DELETED" value="DELETED" />
              </el-select>
            </el-form-item>
            <el-form-item label="索引状态">
              <el-select v-model="documentQuery.indexStatus" clearable placeholder="全部">
                <el-option label="PENDING" value="PENDING" />
                <el-option label="INDEXED" value="INDEXED" />
                <el-option label="FAILED" value="FAILED" />
              </el-select>
            </el-form-item>
            <el-form-item label="标题">
              <el-input v-model.trim="documentQuery.title" clearable placeholder="文档标题" />
            </el-form-item>
            <el-form-item label="关键词">
              <el-input v-model.trim="documentQuery.keyword" clearable placeholder="标题/来源/元数据" />
            </el-form-item>
            <el-form-item class="filter-actions">
              <el-button type="primary" :icon="Search" @click="searchDocuments">
                查询
              </el-button>
              <el-button @click="resetDocumentQuery">重置</el-button>
            </el-form-item>
          </el-form>

          <el-table
            v-loading="documentLoading"
            :data="documents"
            border
            stripe
          >
            <el-table-column prop="id" label="文档ID" width="100" fixed />
            <el-table-column prop="userId" label="用户ID" width="110">
              <template #default="{ row }">
                <el-tag :type="row.userId === 0 ? 'success' : 'warning'" effect="plain">
                  {{ row.userId === 0 ? "公共" : row.userId }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="documentType" label="类型" width="190" />
            <el-table-column prop="businessId" label="业务ID" width="110" />
            <el-table-column prop="title" label="标题" min-width="220" show-overflow-tooltip />
            <el-table-column prop="permissionScope" label="权限" width="110">
              <template #default="{ row }">
                <el-tag :type="tagType(row.permissionScope)" effect="plain">
                  {{ row.permissionScope }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="chunkCount" label="切片数" width="90" />
            <el-table-column prop="status" label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="tagType(row.status)" effect="plain">
                  {{ row.status }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="indexStatus" label="索引" width="110">
              <template #default="{ row }">
                <el-tag :type="tagType(row.indexStatus)" effect="plain">
                  {{ row.indexStatus }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="lastIndexTime" label="索引时间" width="170" />
            <el-table-column label="操作" width="260" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" :icon="View" @click="viewDocumentChunks(row)">
                  查看切片
                </el-button>
                <el-button
                  link
                  type="warning"
                  :loading="documentActionKey === `index-${row.id}`"
                  @click="runIndexDocument(row)"
                >
                  重建
                </el-button>
                <el-button
                  link
                  type="danger"
                  :loading="documentActionKey === `delete-${row.id}`"
                  @click="runDeleteDocument(row)"
                >
                  删除同步
                </el-button>
              </template>
            </el-table-column>
          </el-table>

          <el-pagination
            class="table-pagination"
            background
            layout="total, sizes, prev, pager, next, jumper"
            :current-page="documentQuery.pageNum"
            :page-size="documentQuery.pageSize"
            :page-sizes="[10, 20, 50, 100]"
            :total="documentTotal"
            @current-change="handleDocumentCurrentChange"
            @size-change="handleDocumentSizeChange"
          />
        </section>
      </el-tab-pane>

      <el-tab-pane label="切片列表" name="chunks">
        <section class="rag-panel">
          <div class="panel-title">
            <h2>RAG 切片</h2>
            <el-button :icon="Refresh" :loading="chunkLoading" @click="loadChunks">
              刷新切片
            </el-button>
          </div>

          <el-form class="filter-form" :model="chunkQuery" label-width="88px">
            <el-form-item label="文档ID">
              <el-input v-model.trim="chunkQuery.documentId" clearable placeholder="rag_document.id" />
            </el-form-item>
            <el-form-item label="用户ID">
              <el-input v-model.trim="chunkQuery.userId" clearable placeholder="0 为公共知识" />
            </el-form-item>
            <el-form-item label="文档类型">
              <el-select v-model="chunkQuery.documentType" clearable placeholder="全部">
                <el-option
                  v-for="item in documentTypeOptions"
                  :key="item"
                  :label="item"
                  :value="item"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="业务ID">
              <el-input v-model.trim="chunkQuery.businessId" clearable placeholder="来源业务ID" />
            </el-form-item>
            <el-form-item label="状态">
              <el-select v-model="chunkQuery.status" clearable placeholder="全部">
                <el-option label="ACTIVE" value="ACTIVE" />
                <el-option label="DELETED" value="DELETED" />
              </el-select>
            </el-form-item>
            <el-form-item label="向量状态">
              <el-select v-model="chunkQuery.vectorStatus" clearable placeholder="全部">
                <el-option label="PENDING" value="PENDING" />
                <el-option label="INDEXED" value="INDEXED" />
                <el-option label="FAILED" value="FAILED" />
              </el-select>
            </el-form-item>
            <el-form-item label="标题">
              <el-input v-model.trim="chunkQuery.title" clearable placeholder="切片标题" />
            </el-form-item>
            <el-form-item label="关键词">
              <el-input v-model.trim="chunkQuery.keyword" clearable placeholder="标题/正文/元数据" />
            </el-form-item>
            <el-form-item class="filter-actions">
              <el-button type="primary" :icon="Search" @click="searchChunks">
                查询
              </el-button>
              <el-button @click="resetChunkQuery">重置</el-button>
            </el-form-item>
          </el-form>

          <el-table
            v-loading="chunkLoading"
            :data="chunks"
            border
            stripe
          >
            <el-table-column prop="id" label="切片ID" width="100" fixed />
            <el-table-column prop="documentId" label="文档ID" width="100" />
            <el-table-column prop="userId" label="用户ID" width="110">
              <template #default="{ row }">
                <el-tag :type="row.userId === 0 ? 'success' : 'warning'" effect="plain">
                  {{ row.userId === 0 ? "公共" : row.userId }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="documentType" label="类型" width="190" />
            <el-table-column prop="businessId" label="业务ID" width="110" />
            <el-table-column prop="chunkIndex" label="序号" width="80" />
            <el-table-column prop="title" label="标题" min-width="220" show-overflow-tooltip />
            <el-table-column prop="content" label="切片内容" min-width="360">
              <template #default="{ row }">
                <span class="content-preview">{{ row.content }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="status" label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="tagType(row.status)" effect="plain">
                  {{ row.status }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="vectorStatus" label="向量" width="110">
              <template #default="{ row }">
                <el-tag :type="tagType(row.vectorStatus)" effect="plain">
                  {{ row.vectorStatus }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="130" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" :icon="View" @click="openChunkDetail(row)">
                  详情
                </el-button>
              </template>
            </el-table-column>
          </el-table>

          <el-pagination
            class="table-pagination"
            background
            layout="total, sizes, prev, pager, next, jumper"
            :current-page="chunkQuery.pageNum"
            :page-size="chunkQuery.pageSize"
            :page-sizes="[10, 20, 50, 100]"
            :total="chunkTotal"
            @current-change="handleChunkCurrentChange"
            @size-change="handleChunkSizeChange"
          />
        </section>
      </el-tab-pane>

      <el-tab-pane label="检索预览" name="search">
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
            <el-table-column prop="referenceNo" label="引用" width="80" />
            <el-table-column prop="score" label="得分" width="100">
              <template #default="{ row }">
                {{ formatScore(row.score) }}
              </template>
            </el-table-column>
            <el-table-column prop="retrievalSource" label="来源" width="110" />
            <el-table-column prop="documentType" label="类型" width="180" />
            <el-table-column prop="documentId" label="文档ID" width="100" />
            <el-table-column prop="chunkId" label="切片ID" width="100" />
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
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="metadataVisible" title="RAG 元数据" width="680px">
      <pre class="json-box">{{ metadataText }}</pre>
    </el-dialog>

    <el-drawer v-model="chunkDetailVisible" title="切片详情" size="52%">
      <div v-loading="chunkDetailLoading" class="chunk-detail">
        <template v-if="chunkDetail">
          <el-descriptions :column="2" border>
            <el-descriptions-item label="切片ID">{{ chunkDetail.id }}</el-descriptions-item>
            <el-descriptions-item label="文档ID">{{ chunkDetail.documentId }}</el-descriptions-item>
            <el-descriptions-item label="用户ID">{{ chunkDetail.userId }}</el-descriptions-item>
            <el-descriptions-item label="文档类型">{{ chunkDetail.documentType }}</el-descriptions-item>
            <el-descriptions-item label="业务ID">{{ chunkDetail.businessId }}</el-descriptions-item>
            <el-descriptions-item label="切片序号">{{ chunkDetail.chunkIndex }}</el-descriptions-item>
            <el-descriptions-item label="状态">{{ chunkDetail.status }}</el-descriptions-item>
            <el-descriptions-item label="向量状态">{{ chunkDetail.vectorStatus }}</el-descriptions-item>
            <el-descriptions-item label="来源">{{ chunkDetail.source || "-" }}</el-descriptions-item>
            <el-descriptions-item label="索引时间">{{ chunkDetail.lastIndexTime || "-" }}</el-descriptions-item>
            <el-descriptions-item label="内容Hash" :span="2">
              {{ chunkDetail.contentHash || "-" }}
            </el-descriptions-item>
          </el-descriptions>

          <h3>切片正文</h3>
          <pre class="content-box">{{ chunkDetail.content }}</pre>

          <h3>元数据</h3>
          <pre class="json-box">{{ formatJson(chunkDetail.metadataJson) }}</pre>
        </template>
      </div>
    </el-drawer>
  </main>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref, watch } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { Refresh, Search, View } from "@element-plus/icons-vue";
import {
  deleteRagDocument,
  getRagChunkDetail,
  getRagStats,
  indexRagDocument,
  pageRagChunks,
  pageRagDocuments,
  rebuildAllRagKnowledge,
  rebuildPublicRagKnowledge,
  rebuildUserRagKnowledge,
  searchRagKnowledge
} from "../../api/rag";
import type {
  RagChunkInfo,
  RagChunkQuery,
  RagDocumentInfo,
  RagDocumentQuery,
  RagIndexResult,
  RagSearchResult,
  RagStats
} from "../../api/types";

const documentTypeOptions = ["JOB", "COMPANY", "RESUME", "COMMUNICATION", "COMMUNICATION_MESSAGE"];

const activeTab = ref("overview");
const stats = ref<RagStats | null>(null);
const lastIndexResult = ref<RagIndexResult | null>(null);
const searchResults = ref<RagSearchResult[]>([]);
const documents = ref<RagDocumentInfo[]>([]);
const chunks = ref<RagChunkInfo[]>([]);
const chunkDetail = ref<RagChunkInfo | null>(null);

const statsLoading = ref(false);
const searchLoading = ref(false);
const documentLoading = ref(false);
const chunkLoading = ref(false);
const chunkDetailLoading = ref(false);
const targetUserId = ref<number | null>(null);
const documentTotal = ref(0);
const chunkTotal = ref(0);
const documentActionKey = ref("");

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

const documentQuery = reactive<RagDocumentQuery>({
  pageNum: 1,
  pageSize: 10,
  userId: "",
  documentType: "",
  businessId: "",
  title: "",
  permissionScope: "",
  status: "",
  indexStatus: "",
  keyword: ""
});

const chunkQuery = reactive<RagChunkQuery>({
  pageNum: 1,
  pageSize: 10,
  documentId: "",
  userId: "",
  documentType: "",
  businessId: "",
  title: "",
  status: "",
  vectorStatus: "",
  keyword: ""
});

const metadataVisible = ref(false);
const metadataText = ref("");
const chunkDetailVisible = ref(false);

onMounted(() => {
  loadStats();
});

watch(activeTab, tab => {
  if (tab === "documents" && documents.value.length === 0) {
    loadDocuments();
  }
  if (tab === "chunks" && chunks.value.length === 0) {
    loadChunks();
  }
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
 * 加载 RAG 文档分页。
 */
async function loadDocuments() {
  documentLoading.value = true;

  try {
    const page = await pageRagDocuments({ ...documentQuery });
    documents.value = page.records || [];
    documentTotal.value = page.total || 0;
    documentQuery.pageNum = page.current || documentQuery.pageNum;
    documentQuery.pageSize = page.size || documentQuery.pageSize;
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : "RAG 文档加载失败");
  } finally {
    documentLoading.value = false;
  }
}

/**
 * 加载 RAG 切片分页。
 */
async function loadChunks() {
  chunkLoading.value = true;

  try {
    const page = await pageRagChunks({ ...chunkQuery });
    chunks.value = page.records || [];
    chunkTotal.value = page.total || 0;
    chunkQuery.pageNum = page.current || chunkQuery.pageNum;
    chunkQuery.pageSize = page.size || chunkQuery.pageSize;
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : "RAG 切片加载失败");
  } finally {
    chunkLoading.value = false;
  }
}

function searchDocuments() {
  documentQuery.pageNum = 1;
  loadDocuments();
}

function searchChunks() {
  chunkQuery.pageNum = 1;
  loadChunks();
}

function resetDocumentQuery() {
  Object.assign(documentQuery, {
    pageNum: 1,
    pageSize: 10,
    userId: "",
    documentType: "",
    businessId: "",
    title: "",
    permissionScope: "",
    status: "",
    indexStatus: "",
    keyword: ""
  });
  loadDocuments();
}

function resetChunkQuery() {
  Object.assign(chunkQuery, {
    pageNum: 1,
    pageSize: 10,
    documentId: "",
    userId: "",
    documentType: "",
    businessId: "",
    title: "",
    status: "",
    vectorStatus: "",
    keyword: ""
  });
  loadChunks();
}

function handleDocumentCurrentChange(pageNum: number) {
  documentQuery.pageNum = pageNum;
  loadDocuments();
}

function handleDocumentSizeChange(pageSize: number) {
  documentQuery.pageSize = pageSize;
  documentQuery.pageNum = 1;
  loadDocuments();
}

function handleChunkCurrentChange(pageNum: number) {
  chunkQuery.pageNum = pageNum;
  loadChunks();
}

function handleChunkSizeChange(pageSize: number) {
  chunkQuery.pageSize = pageSize;
  chunkQuery.pageNum = 1;
  loadChunks();
}

/**
 * 从文档行跳转到切片列表。
 *
 * @param row 当前 RAG 文档
 */
function viewDocumentChunks(row: RagDocumentInfo) {
  activeTab.value = "chunks";
  Object.assign(chunkQuery, {
    pageNum: 1,
    documentId: row.id,
    userId: "",
    documentType: "",
    businessId: "",
    title: "",
    status: "",
    vectorStatus: "",
    keyword: ""
  });
  loadChunks();
}

/**
 * 重建单个业务文档的 RAG 索引。
 *
 * @param row 当前 RAG 文档
 */
async function runIndexDocument(row: RagDocumentInfo) {
  documentActionKey.value = `index-${row.id}`;

  try {
    lastIndexResult.value = await indexRagDocument(row.userId, row.documentType, row.businessId);
    ElMessage.success("单文档索引完成");
    await Promise.all([loadStats(), loadDocuments(), loadChunks()]);
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : "单文档索引失败");
  } finally {
    documentActionKey.value = "";
  }
}

/**
 * 删除同步会同时让 MySQL 可视化知识和 pgvector 向量失效。
 *
 * @param row 当前 RAG 文档
 */
async function runDeleteDocument(row: RagDocumentInfo) {
  try {
    await ElMessageBox.confirm(
      `确认删除同步 ${row.documentType}#${row.businessId} 吗？`,
      "删除同步",
      {
        type: "warning",
        confirmButtonText: "确认",
        cancelButtonText: "取消"
      }
    );
  } catch {
    return;
  }

  documentActionKey.value = `delete-${row.id}`;

  try {
    lastIndexResult.value = await deleteRagDocument(row.userId, row.documentType, row.businessId);
    ElMessage.success("删除同步完成");
    await Promise.all([loadStats(), loadDocuments(), loadChunks()]);
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : "删除同步失败");
  } finally {
    documentActionKey.value = "";
  }
}

/**
 * 打开切片详情抽屉。
 *
 * @param row 当前 RAG 切片
 */
async function openChunkDetail(row: RagChunkInfo) {
  chunkDetailVisible.value = true;
  chunkDetailLoading.value = true;
  chunkDetail.value = null;

  try {
    chunkDetail.value = await getRagChunkDetail(row.id);
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : "切片详情加载失败");
  } finally {
    chunkDetailLoading.value = false;
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
    await Promise.all([loadStats(), loadDocuments(), loadChunks()]);
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
function openMetadata(metadata?: Record<string, unknown> | string) {
  metadataText.value = formatJson(metadata);
  metadataVisible.value = true;
}

function formatJson(value?: Record<string, unknown> | string) {
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

function formatScore(score?: number) {
  return typeof score === "number" ? score.toFixed(4) : "-";
}

function tagType(value?: string) {
  if (value === "ACTIVE" || value === "INDEXED" || value === "PUBLIC") {
    return "success";
  }
  if (value === "PENDING") {
    return "warning";
  }
  if (value === "FAILED" || value === "DELETED") {
    return "danger";
  }
  return "info";
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

.rag-tabs {
  display: grid;
  gap: 16px;
}

.index-actions,
.search-row {
  margin-top: 14px;
  flex-wrap: wrap;
}

.index-result,
.table-pagination {
  margin-top: 14px;
}

.rag-status-alert {
  border-radius: 8px;
}

.filter-form {
  display: grid;
  grid-template-columns: repeat(4, minmax(180px, 1fr));
  gap: 12px 16px;
  margin: 16px 0;
}

.filter-form :deep(.el-form-item) {
  margin-bottom: 0;
}

.filter-actions {
  align-items: flex-end;
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

.chunk-detail {
  display: grid;
  gap: 16px;
}

.chunk-detail h3 {
  margin: 8px 0 0;
  color: #111827;
  font-size: 16px;
}

.content-box,
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

@media (max-width: 1180px) {
  .filter-form {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 980px) {
  .rag-header,
  .panel-title,
  .index-actions,
  .search-row {
    align-items: stretch;
    flex-direction: column;
  }

  .rag-metrics,
  .filter-form {
    grid-template-columns: 1fr;
  }

  .search-row .el-input,
  .search-row .el-input-number,
  .index-actions .el-input-number {
    width: 100%;
  }
}
</style>
