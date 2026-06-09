<template>
  <div class="communication-page">
    <div class="page-container">
      <!-- 顶部说明卡片：风格对齐求职进度页 -->
      <section class="hero-card">
        <div>
          <div class="hero-label">求职沟通记录</div>
          <h1>管理你的 HR 沟通进度</h1>
          <p>
            记录打招呼语、HR 回复、面试邀约和后续跟进，把 Boss 直聘等平台的沟通过程沉淀到 Job-Agent。
          </p>
        </div>

        <el-button type="primary" class="refresh-btn" @click="loadPage">
          刷新记录
        </el-button>
      </section>

      <!-- 顶部核心统计：和求职进度页一样做 3 个大卡片 -->
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

      <!-- 状态筛选卡片：对齐求职进度页下面那一组状态卡 -->
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

      <!-- 查询区域：保持和求职进度页一致的白色卡片 -->
      <section class="filter-card">
        <div class="filter-item">
          <label>关键词</label>
          <el-input
            v-model="query.keyword"
            clearable
            placeholder="搜索 HR、回复、备注"
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

      <!-- 沟通记录列表：不用表格，改成和求职进度一样的卡片列表 -->
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
              <h3>
                {{ record.jobTitle || "岗位信息已失效" }}
              </h3>

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

            <p class="record-meta">
              <span>公司：{{ record.companyName || "未知公司" }}</span>
              <span>城市：{{ record.jobCity || "未知城市" }}</span>
              <span>薪资：{{ record.salaryText || "薪资面议" }}</span>
            </p>

            <p class="record-meta">
              <span>简历：{{ record.resumeName || "未关联简历" }}</span>
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

            <p class="record-action">
              最近动作：
              <span>{{ latestActionText(record) }}</span>
            </p>
          </div>

          <!-- 右侧操作区：对齐求职进度页按钮风格 -->
          <div class="record-actions">
            <el-select
              :model-value="record.communicationStatus"
              size="small"
              class="status-select"
              @change="(value: string) => handleStatusChange(record, value)"
            >
              <el-option label="已生成话术" value="GREETING_GENERATED" />
              <el-option label="已复制" value="COPIED" />
              <el-option label="已沟通" value="COMMUNICATED" />
              <el-option label="已回复" value="REPLIED" />
              <el-option label="邀约面试" value="INTERVIEW_INVITED" />
              <el-option label="暂无回复" value="NO_REPLY" />
              <el-option label="已关闭" value="CLOSED" />
            </el-select>

            <div class="action-buttons">
              <el-button size="small" type="primary" plain @click="copyGreeting(record)">
                复制话术
              </el-button>

              <el-button size="small" type="success" plain @click="openReplyDialog(record)">
                录入回复
              </el-button>

              <el-button size="small" type="primary" plain @click="openInterviewDialog(record)">
                面试邀约
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

        <!-- 分页：卡片式页面底部分页 -->
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

    <!-- HR 回复弹窗 -->
    <el-dialog v-model="replyDialogVisible" title="录入 HR 回复" width="520px">
      <el-form label-position="top">
        <el-form-item label="HR 回复内容">
          <el-input
            v-model="replyForm.hrReply"
            type="textarea"
            :rows="5"
            placeholder="例如：明天下午 3 点方便面试吗？"
          />
        </el-form-item>

        <el-form-item label="备注">
          <el-input
            v-model="replyForm.note"
            type="textarea"
            :rows="3"
            placeholder="可选：记录后续跟进事项"
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="replyDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitReply">保存</el-button>
      </template>
    </el-dialog>

    <!-- 面试邀约弹窗 -->
    <el-dialog v-model="interviewDialogVisible" title="标记面试邀约" width="520px">
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
        <el-button @click="interviewDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitInterview">保存</el-button>
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
  getCommunicationStats,
  markCommunicationCommunicated,
  markCommunicationCopied,
  markCommunicationInterview,
  pageCommunications,
  saveCommunicationReply
} from "../api/communication";
import type {
  CommunicationRecordInfo,
  CommunicationStatsInfo
} from "../api/types";

/**
 * 路由对象。
 *
 * 用途：
 * 1. 点击“查看岗位”时跳转岗位详情页。
 * 2. 让沟通记录和岗位详情形成业务闭环。
 */
const router = useRouter();

/**
 * 页面加载状态。
 */
const loading = ref(false);

/**
 * 沟通记录列表。
 */
const records = ref<CommunicationRecordInfo[]>([]);

/**
 * 分页总数。
 */
const total = ref(0);

/**
 * 查询条件。
 *
 * 说明：
 * 1. status 对应后端 communicationStatus。
 * 2. platform 对应 BOSS、LAGOU、LIEPIN 等。
 * 3. keyword 用于搜索 HR、回复、备注。
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
 */
const stats = reactive<CommunicationStatsInfo>({
  totalCount: 0,
  greetingGeneratedCount: 0,
  copiedCount: 0,
  communicatedCount: 0,
  repliedCount: 0,
  interviewInvitedCount: 0,
  noReplyCount: 0,
  closedCount: 0
});

/**
 * HR 回复弹窗状态。
 */
const replyDialogVisible = ref(false);

/**
 * 面试邀约弹窗状态。
 */
const interviewDialogVisible = ref(false);

/**
 * HR 回复表单。
 */
const replyForm = reactive({
  id: 0,
  hrReply: "",
  note: ""
});

/**
 * 面试邀约表单。
 */
const interviewForm = reactive({
  id: 0,
  interviewTime: "",
  nextFollowTime: "",
  note: ""
});

/**
 * 状态卡片数据。
 *
 * 这个结构是为了让沟通记录页和求职进度页一样：
 * 上面是统计卡，下面是可点击的状态筛选卡。
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
    label: "已回复",
    value: "REPLIED",
    count: stats.repliedCount
  },
  {
    label: "邀约面试",
    value: "INTERVIEW_INVITED",
    count: stats.interviewInvitedCount
  },
  {
    label: "暂无回复",
    value: "NO_REPLY",
    count: stats.noReplyCount
  },
  {
    label: "已关闭",
    value: "CLOSED",
    count: stats.closedCount
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
 * 复制打招呼语。
 *
 * 业务逻辑：
 * 1. 复制话术到剪贴板。
 * 2. 调用后端接口把状态标记为 COPIED。
 * 3. 用户可以去 Boss 直聘粘贴发送。
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
 * 处理状态下拉框变化。
 *
 * 说明：
 * 当前第一版只处理几个关键状态。
 * 后续你可以补充 NO_REPLY、CLOSED 的专门接口。
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
  } else if (status === "INTERVIEW_INVITED") {
    openInterviewDialog(record);
    return;
  } else if (status === "CLOSED") {
    await handleClose(record);
    return;
  } else {
    ElMessage.info("该状态暂未开放直接切换");
    return;
  }

  await loadPage();
}

/**
 * 打开 HR 回复弹窗。
 */
function openReplyDialog(record: CommunicationRecordInfo) {
  replyForm.id = record.id;
  replyForm.hrReply = record.hrReply || "";
  replyForm.note = record.note || "";
  replyDialogVisible.value = true;
}

/**
 * 保存 HR 回复。
 */
async function submitReply() {
  if (!replyForm.hrReply.trim()) {
    ElMessage.warning("请输入 HR 回复内容");
    return;
  }

  await saveCommunicationReply(replyForm.id, {
    hrReply: replyForm.hrReply,
    note: replyForm.note
  });

  ElMessage.success("HR 回复已保存");

  replyDialogVisible.value = false;

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
    interviewTime: interviewForm.interviewTime || undefined,
    nextFollowTime: interviewForm.nextFollowTime || undefined,
    note: interviewForm.note
  });

  ElMessage.success("已标记面试邀约");

  interviewDialogVisible.value = false;

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
    return "HR 已回复，建议根据回复判断是否需要跟进";
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
    REPLIED: "已回复",
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
 * 格式化时间。
 */
function formatTime(value?: string) {
  if (!value) {
    return "";
  }

  return value.replace("T", " ").slice(0, 16);
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
  font-size: 14px;
  color: #667085;
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
  width: 160px;
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

@media (max-width: 980px) {
  .page-container {
    width: calc(100% - 28px);
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