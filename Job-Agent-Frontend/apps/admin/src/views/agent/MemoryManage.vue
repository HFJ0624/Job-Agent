<template>
  <main class="admin-page">
    <section class="page-header">
      <div>
        <p class="eyebrow">Agent Memory</p>
        <h1>Agent 长期记忆</h1>
        <p>查看用户长期记忆画像、原始记忆、召回状态，并人工禁用错误记忆。</p>
      </div>

      <el-button type="primary" :loading="loading" @click="loadMemories">
        刷新
      </el-button>
    </section>

    <section class="profile-card">
      <div class="profile-toolbar">
        <div>
          <h2>用户记忆画像</h2>
          <p>画像是注入 Agent Prompt 的压缩记忆，错误记忆禁用后需要重建画像。</p>
        </div>

        <div class="profile-actions">
          <el-input
            v-model.trim="profileUserId"
            placeholder="输入用户ID"
            clearable
            class="profile-user-input"
          />
          <el-button :loading="profileLoading" @click="loadProfile">
            查询画像
          </el-button>
          <el-button type="warning" :loading="profileLoading" @click="rebuildProfile">
            重建画像
          </el-button>
        </div>
      </div>

      <div v-if="profile" class="profile-grid">
        <div>
          <span>用户ID</span>
          <strong>{{ profile.userId || "-" }}</strong>
        </div>
        <div>
          <span>有效记忆数</span>
          <strong>{{ profile.memoryCount || 0 }}</strong>
        </div>
        <div>
          <span>画像版本</span>
          <strong>v{{ profile.profileVersion || 1 }}</strong>
        </div>
        <div>
          <span>最近构建</span>
          <strong>{{ profile.lastBuildTime || "-" }}</strong>
        </div>
      </div>

      <pre v-if="profile" class="profile-summary">{{ profile.profileSummary || "暂无画像摘要" }}</pre>
      <el-empty v-else description="请输入用户ID查询画像" />
    </section>

    <section class="filter-card">
      <el-form :model="query" label-width="90px" class="filter-form">
        <el-row :gutter="12">
          <el-col :span="6">
            <el-form-item label="用户ID">
              <el-input v-model.trim="query.userId" placeholder="输入用户ID" clearable />
            </el-form-item>
          </el-col>

          <el-col :span="6">
            <el-form-item label="记忆类型">
              <el-select v-model="query.memoryType" placeholder="全部" clearable>
                <el-option label="用户偏好" value="USER_PREFERENCE" />
                <el-option label="简历画像" value="RESUME_PROFILE" />
                <el-option label="面试反馈" value="INTERVIEW_FEEDBACK" />
                <el-option label="岗位决策" value="JOB_DECISION" />
                <el-option label="沟通风格" value="COMMUNICATION_STYLE" />
                <el-option label="能力短板" value="SKILL_GAP" />
                <el-option label="职业目标" value="CAREER_GOAL" />
              </el-select>
            </el-form-item>
          </el-col>

          <el-col :span="6">
            <el-form-item label="状态">
              <el-select v-model="query.status" placeholder="全部" clearable>
                <el-option label="有效" value="ACTIVE" />
                <el-option label="已归档" value="ARCHIVED" />
                <el-option label="无效" value="INVALID" />
              </el-select>
            </el-form-item>
          </el-col>

          <el-col :span="6">
            <el-form-item label="来源">
              <el-select v-model="query.sourceType" placeholder="全部" clearable>
                <el-option label="用户消息" value="USER_MESSAGE" />
                <el-option label="计划抽取" value="AGENT_PLAN" />
                <el-option label="工具结果" value="TOOL_RESULT" />
                <el-option label="后台人工" value="ADMIN_MANUAL" />
              </el-select>
            </el-form-item>
          </el-col>

          <el-col :span="6">
            <el-form-item label="记忆键">
              <el-input v-model.trim="query.memoryKey" placeholder="preferred_city" clearable />
            </el-form-item>
          </el-col>

          <el-col :span="6">
            <el-form-item label="关键词">
              <el-input v-model.trim="query.keyword" placeholder="摘要或正文关键词" clearable />
            </el-form-item>
          </el-col>

          <el-col :span="12">
            <el-form-item label="时间范围">
              <el-date-picker
                v-model="timeRange"
                type="datetimerange"
                start-placeholder="开始时间"
                end-placeholder="结束时间"
                value-format="YYYY-MM-DD HH:mm:ss"
                clearable
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
        </el-row>

        <div class="filter-actions">
          <el-button type="primary" :loading="loading" @click="handleSearch">
            查询
          </el-button>
          <el-button @click="resetSearch">
            重置
          </el-button>
        </div>
      </el-form>
    </section>

    <section class="table-card">
      <el-table
        v-loading="loading"
        :data="memories"
        border
        stripe
        style="width: 100%"
      >
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="userId" label="用户ID" width="100" />
        <el-table-column prop="memoryType" label="类型" width="150">
          <template #default="{ row }">
            <el-tag type="info">{{ memoryTypeText(row.memoryType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="memoryKey" label="记忆键" min-width="160">
          <template #default="{ row }">
            <span>{{ row.memoryKey || "-" }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="summary" label="摘要" min-width="260">
          <template #default="{ row }">
            <el-tooltip :content="row.summary || row.memoryValue || '-'" placement="top">
              <span class="ellipsis-text">{{ row.summary || row.memoryValue || "-" }}</span>
            </el-tooltip>
          </template>
        </el-table-column>
        <el-table-column prop="sourceType" label="来源" width="120">
          <template #default="{ row }">
            <span>{{ sourceTypeText(row.sourceType) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="confidence" label="置信度" width="90" />
        <el-table-column prop="importance" label="重要性" width="90" />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)">
              {{ statusText(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="lastUsedTime" label="最近召回" width="170">
          <template #default="{ row }">
            <span>{{ row.lastUsedTime || "-" }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="updateTime" label="更新时间" width="170" />

        <el-table-column label="操作" fixed="right" width="210">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">
              详情
            </el-button>
            <el-button
              v-if="row.status !== 'ACTIVE'"
              link
              type="success"
              @click="changeStatus(row, 'ACTIVE')"
            >
              启用
            </el-button>
            <el-button
              v-if="row.status === 'ACTIVE'"
              link
              type="warning"
              @click="changeStatus(row, 'ARCHIVED')"
            >
              禁用
            </el-button>
            <el-button
              v-if="row.status !== 'INVALID'"
              link
              type="danger"
              @click="changeStatus(row, 'INVALID')"
            >
              判无效
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-row">
        <el-pagination
          v-model:current-page="query.pageNum"
          v-model:page-size="query.pageSize"
          :total="total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="loadMemories"
          @current-change="loadMemories"
        />
      </div>
    </section>

    <el-dialog v-model="detailVisible" title="长期记忆详情" width="860px">
      <div v-if="currentMemory" class="detail-grid">
        <div>
          <span>记忆ID</span>
          <strong>{{ currentMemory.id }}</strong>
        </div>
        <div>
          <span>用户ID</span>
          <strong>{{ currentMemory.userId }}</strong>
        </div>
        <div>
          <span>类型</span>
          <strong>{{ memoryTypeText(currentMemory.memoryType) }}</strong>
        </div>
        <div>
          <span>状态</span>
          <strong>{{ statusText(currentMemory.status) }}</strong>
        </div>
        <div>
          <span>来源</span>
          <strong>{{ sourceTypeText(currentMemory.sourceType) }}</strong>
        </div>
        <div>
          <span>来源ID</span>
          <strong>{{ currentMemory.sourceId || "-" }}</strong>
        </div>
        <div>
          <span>置信度</span>
          <strong>{{ currentMemory.confidence ?? "-" }}</strong>
        </div>
        <div>
          <span>重要性</span>
          <strong>{{ currentMemory.importance ?? "-" }}</strong>
        </div>
      </div>

      <el-divider />

      <h3>摘要</h3>
      <p class="text-block">{{ currentMemory?.summary || "-" }}</p>

      <h3>记忆正文</h3>
      <pre class="memory-value">{{ currentMemory?.memoryValue || "-" }}</pre>
    </el-dialog>
  </main>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref, watch } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import {
  getAgentMemoryDetail,
  getAgentMemoryProfile,
  pageAgentMemories,
  rebuildAgentMemoryProfile,
  updateAgentMemoryStatus
} from "../../api/agentMemory";
import type {
  AgentMemoryInfo,
  AgentMemoryQuery,
  AgentUserMemoryProfileInfo
} from "../../api/types";

/**
 * 记忆列表查询条件。
 * 说明：分页和筛选共用一个对象，保持和后端 AgentMemoryQueryDTO 字段一致。
 */
const query = reactive<AgentMemoryQuery>({
  pageNum: 1,
  pageSize: 10,
  userId: "",
  memoryType: "",
  memoryKey: "",
  sourceType: "",
  status: "",
  keyword: "",
  startTime: "",
  endTime: ""
});

/**
 * 时间范围组件绑定值。
 */
const timeRange = ref<string[] | null>(null);

/**
 * 表格和画像状态。
 */
const memories = ref<AgentMemoryInfo[]>([]);
const total = ref(0);
const loading = ref(false);
const profileUserId = ref("");
const profile = ref<AgentUserMemoryProfileInfo | null>(null);
const profileLoading = ref(false);

/**
 * 详情弹窗状态。
 */
const detailVisible = ref(false);
const currentMemory = ref<AgentMemoryInfo | null>(null);

onMounted(() => {
  loadMemories();
});

/**
 * 监听时间范围变化，同步到后端查询参数。
 */
watch(timeRange, value => {
  query.startTime = value?.[0] || "";
  query.endTime = value?.[1] || "";
});

/**
 * 加载长期记忆列表。
 *
 * 方法步骤:
 * 1. 调用后台分页接口，读取原始长期记忆。
 * 2. 将 records 写入表格，将 total 写入分页组件。
 * 3. 如果查询条件里带了 userId，就同步到画像查询框，方便管理员连续排查同一用户。
 */
async function loadMemories() {
  loading.value = true;

  try {
    const page = await pageAgentMemories(query);
    memories.value = page.records || [];
    total.value = page.total || 0;

    if (query.userId) {
      profileUserId.value = String(query.userId);
    }
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : "长期记忆加载失败");
  } finally {
    loading.value = false;
  }
}

/**
 * 加载用户长期记忆画像。
 *
 * 方法步骤:
 * 1. 校验用户 ID，避免发空请求。
 * 2. 调用 profile 接口读取当前压缩画像。
 * 3. 没有画像时给出空状态，由管理员决定是否手动重建。
 */
async function loadProfile() {
  if (!profileUserId.value) {
    ElMessage.warning("请先输入用户ID");
    return;
  }

  profileLoading.value = true;

  try {
    profile.value = await getAgentMemoryProfile(profileUserId.value);
    if (!profile.value) {
      ElMessage.info("该用户暂无长期记忆画像");
    }
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : "记忆画像加载失败");
  } finally {
    profileLoading.value = false;
  }
}

/**
 * 手动重建用户长期记忆画像。
 *
 * 方法步骤:
 * 1. 校验用户 ID。
 * 2. 调用重建接口，后端只会读取 ACTIVE 记忆生成画像。
 * 3. 将重建后的画像直接展示在页面上。
 */
async function rebuildProfile() {
  if (!profileUserId.value) {
    ElMessage.warning("请先输入用户ID");
    return;
  }

  profileLoading.value = true;

  try {
    profile.value = await rebuildAgentMemoryProfile(profileUserId.value);
    ElMessage.success("用户记忆画像已重建");
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : "记忆画像重建失败");
  } finally {
    profileLoading.value = false;
  }
}

/**
 * 点击查询。
 */
function handleSearch() {
  query.pageNum = 1;
  loadMemories();
}

/**
 * 重置查询条件。
 */
function resetSearch() {
  query.pageNum = 1;
  query.pageSize = 10;
  query.userId = "";
  query.memoryType = "";
  query.memoryKey = "";
  query.sourceType = "";
  query.status = "";
  query.keyword = "";
  query.startTime = "";
  query.endTime = "";
  timeRange.value = null;
  loadMemories();
}

/**
 * 打开详情弹窗。
 *
 * @param row 当前记忆行
 */
async function openDetail(row: AgentMemoryInfo) {
  try {
    currentMemory.value = await getAgentMemoryDetail(row.id);
    detailVisible.value = true;
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : "记忆详情加载失败");
  }
}

/**
 * 更新长期记忆状态。
 *
 * 方法步骤:
 * 1. 弹窗确认，避免管理员误禁用重要事实。
 * 2. 调用状态更新接口，后端会同步重建该用户画像。
 * 3. 刷新列表，并把当前用户画像重新加载出来。
 *
 * @param row 当前记忆行
 * @param status 目标状态
 */
async function changeStatus(row: AgentMemoryInfo, status: string) {
  const actionText = statusText(status);

  try {
    await ElMessageBox.confirm(
      `确认将记忆 ${row.id} 标记为「${actionText}」吗？状态变化后会重建该用户画像。`,
      "确认更新记忆状态",
      { type: "warning" }
    );

    await updateAgentMemoryStatus(row.id, status);
    ElMessage.success("记忆状态已更新，用户画像已重建");
    await loadMemories();

    profileUserId.value = String(row.userId);
    await loadProfile();
  } catch (error) {
    /*
     * Element Plus 的取消操作也会进入 catch。
     * 这里不展示取消提示，避免管理员正常取消时出现误报。
     */
    if (error === "cancel" || error === "close") {
      return;
    }
    ElMessage.error(error instanceof Error ? error.message : "记忆状态更新失败");
  }
}

function memoryTypeText(type?: string) {
  const map: Record<string, string> = {
    USER_PREFERENCE: "用户偏好",
    RESUME_PROFILE: "简历画像",
    INTERVIEW_FEEDBACK: "面试反馈",
    JOB_DECISION: "岗位决策",
    COMMUNICATION_STYLE: "沟通风格",
    SKILL_GAP: "能力短板",
    CAREER_GOAL: "职业目标"
  };
  return type ? map[type] || type : "-";
}

function sourceTypeText(type?: string) {
  const map: Record<string, string> = {
    USER_MESSAGE: "用户消息",
    AGENT_PLAN: "计划抽取",
    TOOL_RESULT: "工具结果",
    ADMIN_MANUAL: "后台人工"
  };
  return type ? map[type] || type : "-";
}

function statusText(status?: string) {
  const map: Record<string, string> = {
    ACTIVE: "有效",
    ARCHIVED: "已归档",
    INVALID: "无效"
  };
  return status ? map[status] || status : "-";
}

function statusType(status?: string) {
  const map: Record<string, string> = {
    ACTIVE: "success",
    ARCHIVED: "warning",
    INVALID: "danger"
  };
  return status ? map[status] || "info" : "info";
}
</script>

<style scoped>
.admin-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.page-header {
  padding: 24px;
  border-radius: 18px;
  background: #ffffff;
  border: 1px solid #e5e7eb;
  display: flex;
  justify-content: space-between;
  gap: 16px;
}

.page-header h1,
.profile-toolbar h2 {
  margin: 4px 0;
  color: #111827;
}

.page-header p,
.profile-toolbar p {
  margin: 0;
  color: #6b7280;
}

.eyebrow {
  margin: 0;
  color: #2563eb;
  font-size: 13px;
  font-weight: 700;
}

.profile-card,
.filter-card,
.table-card {
  padding: 18px;
  border-radius: 18px;
  background: #ffffff;
  border: 1px solid #e5e7eb;
}

.profile-toolbar {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}

.profile-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.profile-user-input {
  width: 220px;
}

.profile-grid,
.detail-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.detail-grid {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.profile-grid div,
.detail-grid div {
  padding: 12px;
  border-radius: 12px;
  background: #f9fafb;
  border: 1px solid #e5e7eb;
}

.profile-grid span,
.detail-grid span {
  display: block;
  color: #6b7280;
  font-size: 12px;
  margin-bottom: 4px;
}

.profile-grid strong,
.detail-grid strong {
  color: #111827;
  word-break: break-all;
}

.profile-summary,
.memory-value,
.text-block {
  margin-top: 14px;
  padding: 14px;
  border-radius: 12px;
  background: #f9fafb;
  border: 1px solid #e5e7eb;
  color: #374151;
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-word;
}

.memory-value {
  max-height: 360px;
  overflow: auto;
}

.filter-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

.pagination-row {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

.ellipsis-text {
  display: inline-block;
  max-width: 240px;
  overflow: hidden;
  text-overflow: ellipsis;
  vertical-align: middle;
  white-space: nowrap;
}
</style>
