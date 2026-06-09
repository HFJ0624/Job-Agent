<template>
  <div class="communication-page">
    <div class="page-container">
      <!-- 顶部说明卡片：风格对齐求职进度页 -->
      <section class="hero-card">
        <div>
          <div class="hero-label">求职沟通记录</div>
          <h1>管理你的 HR 沟通进度</h1>
          <p>
            记录打招呼语、HR 回复、AI 建议回复、面试邀约和后续跟进，
            把 Boss 直聘等平台的沟通过程沉淀到 Job-Agent。
          </p>
        </div>

        <el-button type="primary" class="refresh-btn" @click="loadPage">
          刷新记录
        </el-button>
      </section>

      <!-- 顶部核心统计 -->
      <section class="top-stats">
        <div class="top-stat-card">
          <span>总记录</span>
          <strong>{{ stats.totalCount }}</strong>
        </div>

        <div class="top-stat-card warning-card">
          <span>已沟通</span>
          <strong>{{ stats.communicatedCount }}</strong>
        </div>

        <div class="top-stat-card primary-card">
          <span>邀约面试</span>
          <strong>{{ stats.interviewInvitedCount }}</strong>
        </div>
      </section>

      <!-- 状态筛选卡片 -->
      <section class="status-grid">
        <button
          v-for="item in statusCards"
          :key="item.value"
          class="status-card"
          :class="{ active: query.status === item.value }"
          @click="selectStatus(item.value)"
        >
          <span>{{ item.label }}</span>
          <strong>{{ item.count }}</strong>
        </button>
      </section>

      <!-- 查询区域 -->
      <section class="filter-card">
        <div class="filter-item">
          <label>关键词</label>
          <el-input
            v-model="query.keyword"
            clearable
            placeholder="搜索 HR、公司、岗位、回复、备注"
            @keyup.enter="handleSearch"
          />
        </div>

        <div class="filter-item">
          <label>平台</label>
          <el-select v-model="query.platform" clearable placeholder="全部平台">
            <el-option label="Boss直聘" value="BOSS" />
            <el-option label="拉勾" value="LAGOU" />
            <el-option label="猎聘" value="LIEPIN" />
            <el-option label="邮件" value="EMAIL" />
            <el-option label="其他" value="OTHER" />
          </el-select>
        </div>

        <el-button type="primary" class="search-btn" @click="handleSearch">
          查询
        </el-button>

        <el-button class="reset-btn" @click="resetSearch">
          重置
        </el-button>
      </section>

      <!-- 沟通记录卡片列表 -->
      <section v-loading="loading" class="record-list">
        <el-empty
          v-if="!loading && records.length === 0"
          description="暂无沟通记录，生成 HR 打招呼语后会自动加入这里"
        />

        <article
          v-for="record in records"
          :key="record.id"
          class="record-card"
        >
          <!-- 左侧主要信息 -->
          <div class="record-main">
            <div class="record-title-row">
              <!-- 不再展示岗位ID，而是展示岗位名称 -->
              <h3>{{ record.jobTitle || "岗位信息已失效" }}</h3>

              <el-tag size="small" type="success">
                {{ record.companyName || "未知公司" }}
              </el-tag>

              <el-tag
                size="small"
                :type="statusTagType(record.communicationStatus)"
              >
                {{ record.communicationStatusDesc || statusText(record.communicationStatus) }}
              </el-tag>

              <el-tag size="small" type="info">
                {{ platformText(record.platform) }}
              </el-tag>
            </div>

            <!-- 展示用户真正关心的信息，不展示数据库 ID -->
            <p class="record-meta">
              <span>城市：{{ record.jobCity || "-" }}</span>
              <span>薪资：{{ record.salaryText || "薪资面议" }}</span>
              <span>简历：{{ record.resumeName || "未关联简历" }}</span>
            </p>

            <p class="record-meta">
              <span>HR：{{ record.hrName || "未填写" }}</span>
              <span>更新时间：{{ formatTime(record.updateTime) || "-" }}</span>
            </p>

            <p class="record-summary">
              打招呼语：
              <span>{{ record.greetingText || "暂无打招呼语" }}</span>
            </p>

            <p class="record-progress">
              HR 回复：
              <span>{{ record.hrReply || "暂无回复" }}</span>
            </p>

            <p class="record-progress">
              AI 回复：
              <span>{{ record.aiReplyText || "暂未生成 AI 回复" }}</span>
            </p>

            <p class="record-action">
              最近动作：
              <span>{{ latestActionText(record) }}</span>
            </p>
          </div>

          <!-- 右侧操作区 -->
          <div class="record-actions">
            <!-- 状态流转选择框 -->
            <el-select
              :model-value="record.communicationStatus"
              size="small"
              class="status-select"
              @change="(value: string) => handleStatusChange(record, value)"
            >
              <el-option label="已生成话术" value="GREETING_GENERATED" />
              <el-option label="已复制" value="COPIED" />
              <el-option label="已沟通" value="COMMUNICATED" />
              <el-option label="HR已回复" value="REPLIED" />
              <el-option label="已生成回复" value="AI_REPLY_GENERATED" />
              <el-option label="已回复HR" value="USER_REPLIED" />
              <el-option label="邀约面试" value="INTERVIEW_INVITED" />
              <el-option label="暂无回复" value="NO_REPLY" />
              <el-option label="已关闭" value="CLOSED" />
            </el-select>

            <div class="action-buttons">
              <el-button size="small" type="primary" plain @click="copyGreeting(record)">
                复制话术
              </el-button>

              <el-button size="small" type="success" plain @click="openReplyDialog(record)">
                HR回复
              </el-button>

              <el-button size="small" type="warning" plain @click="copyAiReplyFromRecord(record)">
                复制AI
              </el-button>

              <el-button size="small" type="success" plain @click="markReplySentFromRecord(record)">
                已发HR
              </el-button>

              <el-button size="small" type="primary" plain @click="openInterviewDialog(record)">
                面试邀约
              </el-button>

              <el-button size="small" type="info" plain @click="openDetail(record)">
                详情
              </el-button>

              <el-button size="small" type="info" plain @click="goJobDetail(record)">
                查看岗位
              </el-button>

              <el-button size="small" type="danger" plain @click="handleClose(record)">
                关闭
              </el-button>
            </div>
          </div>
        </article>

        <!-- 分页 -->
        <div v-if="records.length > 0" class="pagination-wrapper">
          <el-pagination
            v-model:current-page="query.pageNo"
            v-model:page-size="query.pageSize"
            :total="total"
            layout="prev, pager, next"
            @current-change="loadPage"
          />
        </div>
      </section>
    </div>

    <!-- 沟通详情抽屉 -->
    <el-drawer
      v-model="detailVisible"
      title="沟通详情"
      size="560px"
    >
      <template v-if="currentRecord">
        <el-descriptions :column="1" border>
          <el-descriptions-item label="岗位">
            {{ currentRecord.jobTitle || "岗位信息已失效" }}
          </el-descriptions-item>

          <el-descriptions-item label="公司">
            {{ currentRecord.companyName || "未知公司" }}
          </el-descriptions-item>

          <el-descriptions-item label="城市">
            {{ currentRecord.jobCity || "-" }}
          </el-descriptions-item>

          <el-descriptions-item label="薪资">
            {{ currentRecord.salaryText || "薪资面议" }}
          </el-descriptions-item>

          <el-descriptions-item label="使用简历">
            {{ currentRecord.resumeName || "未关联简历" }}
          </el-descriptions-item>

          <el-descriptions-item label="平台">
            {{ platformText(currentRecord.platform) }}
          </el-descriptions-item>

          <el-descriptions-item label="状态">
            <el-tag :type="statusTagType(currentRecord.communicationStatus)">
              {{ currentRecord.communicationStatusDesc || statusText(currentRecord.communicationStatus) }}
            </el-tag>
          </el-descriptions-item>

          <el-descriptions-item label="面试时间">
            {{ formatTime(currentRecord.interviewTime) || "-" }}
          </el-descriptions-item>
        </el-descriptions>

        <el-divider />

        <h4>沟通消息流水</h4>

        <el-empty
          v-if="messages.length === 0"
          description="暂无消息流水"
        />

        <el-timeline v-else>
          <el-timeline-item
            v-for="message in messages"
            :key="message.id"
            :timestamp="formatTime(message.createTime)"
            :type="messageTimelineType(message.senderType)"
          >
            <div class="message-card">
              <div class="message-title">
                {{ message.senderTypeDesc || messageTypeText(message.senderType) }}
              </div>

              <div class="message-content">
                {{ message.messageContent }}
              </div>

              <div v-if="message.replyStyle" class="message-meta">
                回复风格：{{ message.replyStyle }}
              </div>
            </div>
          </el-timeline-item>
        </el-timeline>
      </template>
    </el-drawer>

    <!-- HR 回复 + AI 生成回复弹窗 -->
    <el-dialog
      v-model="replyDialogVisible"
      title="录入 HR 回复并生成回复"
      width="640px"
    >
      <el-form label-position="top">
        <el-form-item label="HR 回复内容">
          <el-input
            v-model="replyForm.hrReply"
            type="textarea"
            :rows="5"
            placeholder="把 Boss 直聘、拉勾、猎聘等平台 HR 回复复制到这里"
          />
        </el-form-item>

        <el-form-item label="当前求职进展">
          <el-select v-model="replyForm.progressStatus" style="width: 100%">
            <el-option label="HR已回复" value="REPLIED" />
            <el-option label="邀约面试" value="INTERVIEW_INVITED" />
            <el-option label="暂无回复" value="NO_REPLY" />
            <el-option label="已关闭" value="CLOSED" />
          </el-select>
        </el-form-item>

        <el-form-item label="回复风格">
          <el-select v-model="replyForm.replyStyle" style="width: 100%">
            <el-option label="自然" value="自然" />
            <el-option label="礼貌" value="礼貌" />
            <el-option label="积极" value="积极" />
            <el-option label="简洁" value="简洁" />
            <el-option label="正式" value="正式" />
          </el-select>
        </el-form-item>

        <el-form-item label="额外要求">
          <el-input
            v-model="replyForm.userRequirement"
            type="textarea"
            :rows="3"
            placeholder="例如：帮我委婉表达明天下午不方便，或者帮我问清楚面试形式"
          />
        </el-form-item>

        <el-form-item label="AI 建议回复">
          <div class="ai-reply-box">
            {{ replyForm.aiReplyText || "点击下方“生成回复”后，这里会显示 AI 建议回复。" }}
          </div>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="replyDialogVisible = false">
          取消
        </el-button>

        <el-button type="primary" :loading="replyGenerating" @click="submitReply">
          生成回复
        </el-button>

        <el-button type="success" @click="copyAiReplyFromDialog">
          复制回复
        </el-button>

        <el-button type="warning" @click="markReplySentFromDialog">
          已发送给HR
        </el-button>
      </template>
    </el-dialog>

    <!-- 面试邀约弹窗 -->
    <el-dialog
      v-model="interviewDialogVisible"
      title="标记面试邀约"
      width="520px"
    >
      <el-form label-position="top">
        <el-form-item label="面试时间">
          <el-date-picker
            v-model="interviewForm.interviewTime"
            type="datetime"
            placeholder="选择面试时间"
            style="width: 100%"
          />
        </el-form-item>

        <el-form-item label="下次跟进时间">
          <el-date-picker
            v-model="interviewForm.nextFollowTime"
            type="datetime"
            placeholder="选择下次跟进时间"
            style="width: 100%"
          />
        </el-form-item>

        <el-form-item label="备注">
          <el-input
            v-model="interviewForm.note"
            type="textarea"
            :rows="3"
            placeholder="例如：线上面试，重点准备项目和 Redis"
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="interviewDialogVisible = false">
          取消
        </el-button>

        <el-button type="primary" @click="submitInterview">
          保存
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { useRouter } from "vue-router";
import { ElMessage, ElMessageBox } from "element-plus";
import {
  closeCommunication,
  generateHrReply,
  getCommunicationStats,
  listCommunicationMessages,
  markCommunicationCommunicated,
  markCommunicationCopied,
  markCommunicationInterview,
  markUserReplySent,
  pageCommunications,
  updateCommunicationStatus
} from "../api/communication";
import type {
  CommunicationMessageInfo,
  CommunicationRecordInfo,
  CommunicationStatsInfo
} from "../api/types";

/**
 * 路由对象。
 *
 * 用途：
 * 点击“查看岗位”时跳转岗位详情页。
 */
const router = useRouter();

/**
 * 页面加载状态。
 */
const loading = ref(false);

/**
 * AI 回复生成按钮 loading。
 */
const replyGenerating = ref(false);

/**
 * 沟通记录列表。
 */
const records = ref<CommunicationRecordInfo[]>([]);

/**
 * 沟通消息流水。
 */
const messages = ref<CommunicationMessageInfo[]>([]);

/**
 * 当前打开详情的记录。
 */
const currentRecord = ref<CommunicationRecordInfo | null>(null);

/**
 * 分页总数。
 */
const total = ref(0);

/**
 * 查询条件。
 */
const query = reactive({
  pageNo: 1,
  pageSize: 10,
  status: "",
  platform: "",
  keyword: ""
});

/**
 * 统计数据。
 *
 * 这里额外兼容 aiReplyGeneratedCount、userRepliedCount。
 * 如果你后端暂时没加这两个字段，页面也不会报错。
 */
const stats = reactive<
  CommunicationStatsInfo & {
    aiReplyGeneratedCount?: number;
    userRepliedCount?: number;
  }
>({
  totalCount: 0,
  greetingGeneratedCount: 0,
  copiedCount: 0,
  communicatedCount: 0,
  repliedCount: 0,
  interviewInvitedCount: 0,
  noReplyCount: 0,
  closedCount: 0,
  aiReplyGeneratedCount: 0,
  userRepliedCount: 0
});

/**
 * 详情抽屉。
 */
const detailVisible = ref(false);

/**
 * HR 回复弹窗。
 */
const replyDialogVisible = ref(false);

/**
 * 面试邀约弹窗。
 */
const interviewDialogVisible = ref(false);

/**
 * HR 回复 + AI 回复表单。
 */
const replyForm = reactive({
  id: 0,

  /**
   * HR 回复内容。
   */
  hrReply: "",

  /**
   * 用户选择的当前求职进展。
   */
  progressStatus: "REPLIED",

  /**
   * AI 回复风格。
   */
  replyStyle: "自然",

  /**
   * 用户额外要求。
   */
  userRequirement: "",

  /**
   * 备注。
   */
  note: "",

  /**
   * AI 生成的建议回复。
   */
  aiReplyText: ""
});

/**
 * 面试邀约表单。
 */
const interviewForm = reactive<{
  id: number;
  interviewTime: string | Date | undefined;
  nextFollowTime: string | Date | undefined;
  note: string;
}>({
  id: 0,
  interviewTime: "",
  nextFollowTime: "",
  note: ""
});

/**
 * 状态卡片数据。
 */
const statusCards = computed(() => [
  {
    label: "已生成话术",
    value: "GREETING_GENERATED",
    count: stats.greetingGeneratedCount
  },
  {
    label: "已复制",
    value: "COPIED",
    count: stats.copiedCount
  },
  {
    label: "已沟通",
    value: "COMMUNICATED",
    count: stats.communicatedCount
  },
  {
    label: "HR已回复",
    value: "REPLIED",
    count: stats.repliedCount
  },
  {
    label: "已生成回复",
    value: "AI_REPLY_GENERATED",
    count: stats.aiReplyGeneratedCount || 0
  },
  {
    label: "已回复HR",
    value: "USER_REPLIED",
    count: stats.userRepliedCount || 0
  },
  {
    label: "邀约面试",
    value: "INTERVIEW_INVITED",
    count: stats.interviewInvitedCount
  },
  {
    label: "全部",
    value: "",
    count: stats.totalCount
  }
]);

/**
 * 加载统计数据。
 */
async function loadStats() {
  const data = await getCommunicationStats();

  stats.totalCount = data.totalCount || 0;
  stats.greetingGeneratedCount = data.greetingGeneratedCount || 0;
  stats.copiedCount = data.copiedCount || 0;
  stats.communicatedCount = data.communicatedCount || 0;
  stats.repliedCount = data.repliedCount || 0;
  stats.interviewInvitedCount = data.interviewInvitedCount || 0;
  stats.noReplyCount = data.noReplyCount || 0;
  stats.closedCount = data.closedCount || 0;

  /**
   * 兼容后端新增字段。
   */
  stats.aiReplyGeneratedCount = (data as any).aiReplyGeneratedCount || 0;
  stats.userRepliedCount = (data as any).userRepliedCount || 0;
}

/**
 * 加载分页数据。
 */
async function loadPage() {
  loading.value = true;

  try {
    const data = await pageCommunications({
      pageNo: query.pageNo,
      pageSize: query.pageSize,
      status: query.status || undefined,
      platform: query.platform || undefined,
      keyword: query.keyword || undefined
    });

    records.value = data.records || [];
    total.value = data.total || 0;

    await loadStats();
  } finally {
    loading.value = false;
  }
}

/**
 * 点击状态卡片筛选。
 */
function selectStatus(status: string) {
  query.status = status;
  query.pageNo = 1;
  loadPage();
}

/**
 * 查询按钮。
 */
function handleSearch() {
  query.pageNo = 1;
  loadPage();
}

/**
 * 重置查询条件。
 */
function resetSearch() {
  query.pageNo = 1;
  query.status = "";
  query.platform = "";
  query.keyword = "";
  loadPage();
}

/**
 * 打开详情抽屉，并加载该沟通记录的消息流水。
 */
async function openDetail(record: CommunicationRecordInfo) {
  currentRecord.value = record;
  detailVisible.value = true;

  messages.value = await listCommunicationMessages(record.id);
}

/**
 * 复制打招呼语。
 *
 * 业务逻辑：
 * 1. 用户复制 AI 生成的开场白。
 * 2. 系统标记状态为 COPIED。
 */
async function copyGreeting(record: CommunicationRecordInfo) {
  if (!record.greetingText) {
    ElMessage.warning("当前记录没有打招呼语");
    return;
  }

  await navigator.clipboard.writeText(record.greetingText);

  await markCommunicationCopied(record.id);

  ElMessage.success("已复制话术，并标记为已复制");

  await loadPage();
}

/**
 * 打开 HR 回复弹窗。
 */
function openReplyDialog(record: CommunicationRecordInfo) {
  replyForm.id = record.id;
  replyForm.hrReply = record.hrReply || "";
  replyForm.progressStatus = record.communicationStatus || "REPLIED";
  replyForm.replyStyle = "自然";
  replyForm.userRequirement = "";
  replyForm.note = record.note || "";
  replyForm.aiReplyText = record.aiReplyText || "";

  replyDialogVisible.value = true;
}

/**
 * 保存 HR 回复并生成 AI 建议回复。
 */
async function submitReply() {
  if (!replyForm.hrReply.trim()) {
    ElMessage.warning("请输入 HR 回复内容");
    return;
  }

  replyGenerating.value = true;

  try {
    const data = await generateHrReply(replyForm.id, {
      hrReply: replyForm.hrReply,
      progressStatus: replyForm.progressStatus,
      replyStyle: replyForm.replyStyle,
      userRequirement: replyForm.userRequirement,
      note: replyForm.note
    });

    replyForm.aiReplyText = data.aiReplyText || "";

    ElMessage.success("AI 回复已生成");

    await loadPage();
  } finally {
    replyGenerating.value = false;
  }
}

/**
 * 复制弹窗里的 AI 回复。
 */
async function copyAiReplyFromDialog() {
  if (!replyForm.aiReplyText) {
    ElMessage.warning("请先生成 AI 回复");
    return;
  }

  await navigator.clipboard.writeText(replyForm.aiReplyText);

  ElMessage.success("AI 回复已复制，可以粘贴到招聘平台发送给 HR");
}

/**
 * 复制卡片里的 AI 回复。
 */
async function copyAiReplyFromRecord(record: CommunicationRecordInfo) {
  if (!record.aiReplyText) {
    ElMessage.warning("当前记录还没有生成 AI 回复");
    return;
  }

  await navigator.clipboard.writeText(record.aiReplyText);

  ElMessage.success("AI 回复已复制");
}

/**
 * 在弹窗中标记已发送给 HR。
 */
async function markReplySentFromDialog() {
  if (!replyForm.aiReplyText) {
    ElMessage.warning("请先生成 AI 回复");
    return;
  }

  await markUserReplySent(replyForm.id, {
    userReplyText: replyForm.aiReplyText
  });

  ElMessage.success("已标记为已回复 HR");

  replyDialogVisible.value = false;

  await loadPage();
}

/**
 * 在卡片中标记已发送给 HR。
 */
async function markReplySentFromRecord(record: CommunicationRecordInfo) {
  const replyText = record.aiReplyText || record.userReplyText;

  if (!replyText) {
    ElMessage.warning("当前记录没有可发送给 HR 的回复内容");
    return;
  }

  await markUserReplySent(record.id, {
    userReplyText: replyText
  });

  ElMessage.success("已标记为已回复 HR");

  await loadPage();
}

/**
 * 打开面试邀约弹窗。
 */
function openInterviewDialog(record: CommunicationRecordInfo) {
  interviewForm.id = record.id;
  interviewForm.interviewTime = record.interviewTime || "";
  interviewForm.nextFollowTime = record.nextFollowTime || "";
  interviewForm.note = record.note || "";

  interviewDialogVisible.value = true;
}

/**
 * 保存面试邀约。
 */
async function submitInterview() {
  await markCommunicationInterview(interviewForm.id, {
    interviewTime: normalizeDateTime(interviewForm.interviewTime),
    nextFollowTime: normalizeDateTime(interviewForm.nextFollowTime),
    note: interviewForm.note
  });

  ElMessage.success("已标记面试邀约");

  interviewDialogVisible.value = false;

  await loadPage();
}

/**
 * 状态下拉框变化。
 *
 * 说明：
 * 1. REPLIED 需要打开弹窗录入 HR 回复并生成 AI 回复。
 * 2. INTERVIEW_INVITED 需要打开面试邀约弹窗。
 * 3. USER_REPLIED 需要确认已有 AI 回复。
 * 4. NO_REPLY / CLOSED 可以直接走状态更新。
 */
async function handleStatusChange(record: CommunicationRecordInfo, status: string) {
  if (status === record.communicationStatus) {
    return;
  }

  if (status === "COPIED") {
    await markCommunicationCopied(record.id);
    ElMessage.success("已标记为已复制");
  } else if (status === "COMMUNICATED") {
    await markCommunicationCommunicated(record.id);
    ElMessage.success("已标记为已沟通");
  } else if (status === "REPLIED") {
    openReplyDialog(record);
    return;
  } else if (status === "AI_REPLY_GENERATED") {
    openReplyDialog(record);
    return;
  } else if (status === "USER_REPLIED") {
    await markReplySentFromRecord(record);
    return;
  } else if (status === "INTERVIEW_INVITED") {
    openInterviewDialog(record);
    return;
  } else if (status === "NO_REPLY") {
    await updateCommunicationStatus(record.id, {
      communicationStatus: "NO_REPLY",
      note: record.note
    });
    ElMessage.success("已标记暂无回复");
  } else if (status === "CLOSED") {
    await handleClose(record);
    return;
  } else {
    await updateCommunicationStatus(record.id, {
      communicationStatus: status,
      note: record.note
    });
  }

  await loadPage();
}

/**
 * 关闭沟通记录。
 */
async function handleClose(record: CommunicationRecordInfo) {
  await ElMessageBox.confirm(
    "确认关闭这条沟通记录吗？关闭后表示你不再跟进该岗位。",
    "提示",
    {
      type: "warning",
      confirmButtonText: "确认关闭",
      cancelButtonText: "取消"
    }
  );

  await closeCommunication(record.id);

  ElMessage.success("已关闭沟通记录");

  await loadPage();
}

/**
 * 跳转岗位详情页。
 *
 * 注意：
 * jobId 是内部跳转参数，可以使用，但页面上不直接展示。
 */
function goJobDetail(record: CommunicationRecordInfo) {
  router.push(`/jobs/${record.jobId}`);
}

/**
 * 获取最近动作文案。
 */
function latestActionText(record: CommunicationRecordInfo) {
  if (record.communicationStatus === "GREETING_GENERATED") {
    return "已生成打招呼语，等待复制到招聘平台";
  }

  if (record.communicationStatus === "COPIED") {
    return "已复制话术，建议前往 Boss 直聘发送给 HR";
  }

  if (record.communicationStatus === "COMMUNICATED") {
    return "已和 HR 沟通，等待回复";
  }

  if (record.communicationStatus === "REPLIED") {
    return "HR 已回复，建议生成一段回复给 HR";
  }

  if (record.communicationStatus === "AI_REPLY_GENERATED") {
    return "AI 已生成建议回复，等待复制发送给 HR";
  }

  if (record.communicationStatus === "USER_REPLIED") {
    return "已回复 HR，等待后续反馈";
  }

  if (record.communicationStatus === "INTERVIEW_INVITED") {
    return `已邀约面试，时间：${formatTime(record.interviewTime) || "待确认"}`;
  }

  if (record.communicationStatus === "NO_REPLY") {
    return "暂未收到回复，可以稍后跟进";
  }

  if (record.communicationStatus === "CLOSED") {
    return "该岗位沟通已关闭";
  }

  return "暂无最近动作";
}

/**
 * 状态标签颜色。
 */
function statusTagType(status: string) {
  switch (status) {
    case "GREETING_GENERATED":
      return "info";
    case "COPIED":
      return "primary";
    case "COMMUNICATED":
      return "success";
    case "REPLIED":
      return "warning";
    case "AI_REPLY_GENERATED":
      return "warning";
    case "USER_REPLIED":
      return "success";
    case "INTERVIEW_INVITED":
      return "success";
    case "NO_REPLY":
      return "danger";
    case "CLOSED":
      return "info";
    default:
      return "info";
  }
}

/**
 * 状态中文。
 */
function statusText(status: string) {
  const map: Record<string, string> = {
    GREETING_GENERATED: "已生成话术",
    COPIED: "已复制",
    COMMUNICATED: "已沟通",
    REPLIED: "HR已回复",
    AI_REPLY_GENERATED: "已生成回复",
    USER_REPLIED: "已回复HR",
    INTERVIEW_INVITED: "邀约面试",
    NO_REPLY: "暂无回复",
    CLOSED: "已关闭"
  };

  return map[status] || status;
}

/**
 * 平台中文。
 */
function platformText(platform?: string) {
  const map: Record<string, string> = {
    BOSS: "Boss直聘",
    LAGOU: "拉勾",
    LIEPIN: "猎聘",
    EMAIL: "邮件",
    OTHER: "其他"
  };

  return platform ? map[platform] || platform : "未知";
}

/**
 * 消息类型中文。
 */
function messageTypeText(type: string) {
  const map: Record<string, string> = {
    HR_TO_USER: "HR回复",
    AI_SUGGESTION: "AI建议回复",
    USER_TO_HR: "已发送给HR",
    STATUS_CHANGE: "状态变更"
  };

  return map[type] || type;
}

/**
 * 消息时间线类型。
 */
function messageTimelineType(type: string) {
  if (type === "HR_TO_USER") {
    return "warning";
  }

  if (type === "AI_SUGGESTION") {
    return "primary";
  }

  if (type === "USER_TO_HR") {
    return "success";
  }

  return "info";
}

/**
 * 格式化时间。
 */
function formatTime(value?: string | Date) {
  if (!value) {
    return "";
  }

  if (value instanceof Date) {
    return formatDate(value);
  }

  return value.replace("T", " ").slice(0, 16);
}

/**
 * Date 转 yyyy-MM-dd HH:mm:ss。
 */
function formatDate(date: Date) {
  const pad = (num: number) => String(num).padStart(2, "0");

  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(
    date.getHours()
  )}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`;
}

/**
 * 提交给后端前统一处理时间。
 */
function normalizeDateTime(value?: string | Date) {
  if (!value) {
    return undefined;
  }

  if (value instanceof Date) {
    return formatDate(value);
  }

  return value;
}

onMounted(() => {
  loadPage();
});
</script>

<style scoped>
.communication-page {
  min-height: 100vh;
  padding: 28px 0 64px;
  background: #f3fbfb;
}

.page-container {
  width: 920px;
  margin: 0 auto;
}

.hero-card {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 26px 28px;
  margin-bottom: 16px;
  border: 1px solid #dce9ef;
  border-radius: 16px;
  background: linear-gradient(135deg, #f7fbff 0%, #ffffff 100%);
  box-shadow: 0 8px 24px rgba(15, 23, 42, 0.04);
}

.hero-label {
  margin-bottom: 6px;
  font-size: 13px;
  color: #667085;
}

.hero-card h1 {
  margin: 0;
  font-size: 26px;
  font-weight: 800;
  color: #101828;
}

.hero-card p {
  margin: 8px 0 0;
  max-width: 680px;
  font-size: 14px;
  color: #667085;
  line-height: 1.7;
}

.refresh-btn {
  min-width: 92px;
  height: 38px;
  border-radius: 8px;
  font-weight: 600;
}

.top-stats {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
  margin-bottom: 12px;
}

.top-stat-card {
  padding: 16px 18px;
  border: 1px solid #e3eaf0;
  border-radius: 12px;
  background: #ffffff;
}

.top-stat-card span {
  display: block;
  margin-bottom: 8px;
  font-size: 13px;
  color: #667085;
}

.top-stat-card strong {
  font-size: 26px;
  color: #101828;
}

.warning-card {
  border-color: #fed7aa;
  background: #fff7ed;
}

.primary-card {
  border-color: #bfdbfe;
  background: #eff6ff;
}

.status-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 10px;
  margin-bottom: 14px;
}

.status-card {
  height: 62px;
  padding: 10px 14px;
  text-align: left;
  cursor: pointer;
  border: 1px solid #e3eaf0;
  border-radius: 12px;
  background: #ffffff;
  transition: all 0.18s ease;
}

.status-card:hover {
  transform: translateY(-1px);
  border-color: #14b8a6;
  box-shadow: 0 8px 20px rgba(20, 184, 166, 0.08);
}

.status-card.active {
  border-color: #3b82f6;
  background: #eff6ff;
  box-shadow: 0 0 0 1px rgba(59, 130, 246, 0.2);
}

.status-card span {
  display: block;
  margin-bottom: 6px;
  font-size: 13px;
  color: #667085;
}

.status-card strong {
  font-size: 20px;
  color: #101828;
}

.filter-card {
  display: grid;
  grid-template-columns: 1.3fr 1fr auto auto;
  gap: 12px;
  align-items: end;
  padding: 16px 18px;
  margin-bottom: 16px;
  border: 1px solid #e3eaf0;
  border-radius: 14px;
  background: #ffffff;
}

.filter-item label {
  display: block;
  margin-bottom: 6px;
  font-size: 13px;
  font-weight: 600;
  color: #344054;
}

.search-btn,
.reset-btn {
  height: 34px;
  border-radius: 8px;
}

.record-list {
  min-height: 240px;
}

.record-card {
  display: flex;
  justify-content: space-between;
  gap: 24px;
  padding: 18px 20px;
  margin-bottom: 12px;
  border: 1px solid #e3eaf0;
  border-radius: 14px;
  background: #ffffff;
  box-shadow: 0 8px 24px rgba(15, 23, 42, 0.04);
}

.record-main {
  flex: 1;
  min-width: 0;
}

.record-title-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 6px;
}

.record-title-row h3 {
  margin: 0;
  font-size: 17px;
  font-weight: 800;
  color: #101828;
}

.record-meta,
.record-summary,
.record-progress,
.record-action {
  margin: 5px 0;
  font-size: 14px;
  color: #475467;
}

.record-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}

.record-summary span,
.record-progress span,
.record-action span {
  color: #667085;
}

.record-summary,
.record-progress {
  max-width: 610px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.record-actions {
  width: 184px;
  flex-shrink: 0;
}

.status-select {
  width: 100%;
  margin-bottom: 10px;
}

.action-buttons {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
}

.action-buttons :deep(.el-button) {
  margin-left: 0;
  border-radius: 8px;
}

.pagination-wrapper {
  display: flex;
  justify-content: center;
  padding: 18px 0 0;
}

.ai-reply-box {
  min-height: 96px;
  padding: 12px;
  border-radius: 10px;
  background: #f7f8fa;
  border: 1px solid #e5e7eb;
  color: #344054;
  line-height: 1.7;
  white-space: pre-wrap;
}

.message-card {
  padding: 10px 12px;
  border: 1px solid #e5e7eb;
  border-radius: 10px;
  background: #ffffff;
}

.message-title {
  margin-bottom: 6px;
  font-weight: 700;
  color: #101828;
}

.message-content {
  color: #475467;
  line-height: 1.7;
  white-space: pre-wrap;
}

.message-meta {
  margin-top: 6px;
  font-size: 12px;
  color: #667085;
}

@media (max-width: 980px) {
  .page-container {
    width: calc(100% - 28px);
  }

  .hero-card {
    flex-direction: column;
    align-items: flex-start;
    gap: 14px;
  }

  .top-stats {
    grid-template-columns: 1fr;
  }

  .status-grid {
    grid-template-columns: repeat(2, 1fr);
  }

  .filter-card {
    grid-template-columns: 1fr;
  }

  .record-card {
    flex-direction: column;
  }

  .record-actions {
    width: 100%;
  }
}
</style>