<template>
  <main class="follow-page">
    <section class="page-header">
      <div>
        <p class="eyebrow">Follow-up Agent</p>
        <h1>求职跟进 Agent</h1>
        <p>查看用户求职跟进明细，配置自动提醒、邮件任务和复盘规则。</p>
      </div>
      <div class="header-actions">
        <el-button :loading="applicationLoading || ruleLoading" @click="refreshCurrent">刷新</el-button>
        <el-button type="primary" :loading="scanLoading" @click="scanRules">手动扫描规则</el-button>
      </div>
    </section>

    <el-tabs v-model="activeTab" class="content-tabs">
      <el-tab-pane label="求职跟进明细" name="applications">
        <section class="filter-card">
          <el-form :model="applicationQuery" label-width="90px" class="filter-form">
            <el-form-item label="用户ID">
              <el-input v-model.trim="applicationQuery.userId" clearable placeholder="用户ID" />
            </el-form-item>
            <el-form-item label="求职状态">
              <el-select v-model="applicationQuery.status" clearable placeholder="全部">
                <el-option v-for="item in applicationStatusOptions" :key="item" :label="item" :value="item" />
              </el-select>
            </el-form-item>
            <el-form-item label="关键词">
              <el-input v-model.trim="applicationQuery.keyword" clearable placeholder="公司 / 岗位 / HR" />
            </el-form-item>
            <el-form-item label="失败邮件">
              <el-switch v-model="applicationQuery.failedEmailOnly" active-text="只看失败" />
            </el-form-item>
            <el-form-item class="filter-actions">
              <el-button type="primary" @click="searchApplications">查询</el-button>
              <el-button @click="resetApplicationQuery">重置</el-button>
            </el-form-item>
          </el-form>
        </section>

        <section class="table-card">
          <el-table v-loading="applicationLoading" :data="applications" border stripe>
            <el-table-column prop="id" label="投递ID" width="90" fixed />
            <el-table-column prop="userId" label="用户ID" width="100" />
            <el-table-column prop="companyName" label="公司" min-width="150" show-overflow-tooltip />
            <el-table-column prop="jobTitle" label="岗位" min-width="180" show-overflow-tooltip />
            <el-table-column prop="status" label="状态" width="130">
              <template #default="{ row }">
                <el-tag effect="plain">{{ row.status || "-" }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="applyTime" label="投递时间" width="170" />
            <el-table-column prop="interviewTime" label="面试时间" width="170" />
            <el-table-column prop="nextFollowTime" label="下次跟进" width="170" />
            <el-table-column label="提醒" width="130">
              <template #default="{ row }">
                {{ row.pendingReminderCount || 0 }}/{{ row.reminderCount || 0 }}
              </template>
            </el-table-column>
            <el-table-column label="邮件任务" width="150">
              <template #default="{ row }">
                <el-tag :type="row.failedEmailTaskCount > 0 ? 'danger' : 'success'" effect="plain">
                  {{ row.failedEmailTaskCount || 0 }}/{{ row.emailTaskCount || 0 }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="latestEmailTaskStatus" label="最近邮件状态" width="150" />
            <el-table-column prop="lastAction" label="最近动作" min-width="180" show-overflow-tooltip />
          </el-table>

          <el-pagination
            class="pagination-row"
            background
            layout="total, sizes, prev, pager, next, jumper"
            :current-page="applicationQuery.pageNum"
            :page-size="applicationQuery.pageSize"
            :page-sizes="[10, 20, 50, 100]"
            :total="applicationTotal"
            @current-change="handleApplicationCurrentChange"
            @size-change="handleApplicationSizeChange"
          />
        </section>
      </el-tab-pane>

      <el-tab-pane label="自动跟进规则" name="rules">
        <section class="filter-card two-column">
          <el-form :model="ruleQuery" label-width="90px" class="filter-form compact">
            <el-form-item label="规则名称">
              <el-input v-model.trim="ruleQuery.ruleName" clearable placeholder="规则名称" />
            </el-form-item>
            <el-form-item label="规则类型">
              <el-select v-model="ruleQuery.ruleType" clearable placeholder="全部">
                <el-option v-for="item in ruleTypeOptions" :key="item" :label="item" :value="item" />
              </el-select>
            </el-form-item>
            <el-form-item label="状态">
              <el-select v-model="ruleQuery.status" clearable placeholder="全部">
                <el-option label="ENABLED" value="ENABLED" />
                <el-option label="DISABLED" value="DISABLED" />
              </el-select>
            </el-form-item>
            <el-form-item class="filter-actions">
              <el-button type="primary" @click="searchRules">查询</el-button>
              <el-button @click="resetRuleQuery">重置</el-button>
            </el-form-item>
          </el-form>
          <div class="rule-actions">
            <el-button type="primary" @click="openRuleDialog()">新增规则</el-button>
          </div>
        </section>

        <section class="table-card">
          <el-table v-loading="ruleLoading" :data="rules" border stripe>
            <el-table-column prop="id" label="ID" width="80" fixed />
            <el-table-column prop="ruleCode" label="规则编码" min-width="190" show-overflow-tooltip />
            <el-table-column prop="ruleName" label="规则名称" min-width="160" show-overflow-tooltip />
            <el-table-column prop="ruleType" label="规则类型" min-width="210" />
            <el-table-column prop="triggerStatus" label="触发状态" width="130" />
            <el-table-column prop="delayMinutes" label="延迟/提前分钟" width="130" />
            <el-table-column prop="reminderType" label="提醒类型" width="120" />
            <el-table-column prop="emailEnabled" label="邮件" width="90">
              <template #default="{ row }">
                <el-tag :type="row.emailEnabled ? 'success' : 'info'" effect="plain">
                  {{ row.emailEnabled ? "开启" : "关闭" }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="status" label="状态" width="110">
              <template #default="{ row }">
                <el-tag :type="row.status === 'ENABLED' ? 'success' : 'info'" effect="plain">{{ row.status }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="updateTime" label="更新时间" width="170" />
            <el-table-column label="操作" fixed="right" width="150">
              <template #default="{ row }">
                <el-button link type="primary" @click="openRuleDialog(row)">编辑</el-button>
                <el-button link type="danger" @click="removeRule(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>

          <el-pagination
            class="pagination-row"
            background
            layout="total, sizes, prev, pager, next, jumper"
            :current-page="ruleQuery.pageNum"
            :page-size="ruleQuery.pageSize"
            :page-sizes="[10, 20, 50, 100]"
            :total="ruleTotal"
            @current-change="handleRuleCurrentChange"
            @size-change="handleRuleSizeChange"
          />
        </section>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="ruleDialogVisible" :title="editingRuleId ? '编辑规则' : '新增规则'" width="720px">
      <el-form :model="ruleForm" label-width="130px" class="rule-form">
        <el-form-item label="规则编码" required>
          <el-input v-model.trim="ruleForm.ruleCode" placeholder="例如 APPLICATION_NO_FEEDBACK_3D" />
        </el-form-item>
        <el-form-item label="规则名称" required>
          <el-input v-model.trim="ruleForm.ruleName" placeholder="例如 投递后三天未反馈提醒" />
        </el-form-item>
        <el-form-item label="规则类型" required>
          <el-select v-model="ruleForm.ruleType" placeholder="请选择">
            <el-option v-for="item in ruleTypeOptions" :key="item" :label="item" :value="item" />
          </el-select>
        </el-form-item>
        <el-form-item label="触发状态">
          <el-input v-model.trim="ruleForm.triggerStatus" placeholder="例如 APPLIED / INTERVIEWING" />
        </el-form-item>
        <el-form-item label="延迟/提前分钟">
          <el-input-number v-model="ruleForm.delayMinutes" :min="-10080" :max="10080" controls-position="right" />
        </el-form-item>
        <el-form-item label="提醒类型">
          <el-select v-model="ruleForm.reminderType" placeholder="请选择">
            <el-option label="INTERVIEW" value="INTERVIEW" />
            <el-option label="FOLLOW_UP" value="FOLLOW_UP" />
            <el-option label="CUSTOM" value="CUSTOM" />
          </el-select>
        </el-form-item>
        <el-form-item label="提醒标题">
          <el-input v-model.trim="ruleForm.reminderTitle" placeholder="提醒标题" />
        </el-form-item>
        <el-form-item label="提醒内容模板">
          <el-input
            v-model="ruleForm.reminderTemplate"
            type="textarea"
            :rows="4"
            placeholder="支持 {companyName}、{jobTitle}、{hrName}、{status}"
          />
        </el-form-item>
        <el-form-item label="发送邮件">
          <el-switch v-model="emailEnabled" />
        </el-form-item>
        <el-form-item label="创建工作流任务">
          <el-switch v-model="workflowEnabled" />
        </el-form-item>
        <el-form-item label="最大重试次数">
          <el-input-number v-model="ruleForm.maxRetryCount" :min="1" :max="10" controls-position="right" />
        </el-form-item>
        <el-form-item label="重试间隔秒">
          <el-input-number v-model="ruleForm.retryIntervalSeconds" :min="30" :max="86400" controls-position="right" />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="ruleForm.status">
            <el-radio-button label="ENABLED" />
            <el-radio-button label="DISABLED" />
          </el-radio-group>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model.trim="ruleForm.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="ruleDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="savingRule" @click="saveRule">保存</el-button>
      </template>
    </el-dialog>
  </main>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import {
  createFollowUpRule,
  deleteFollowUpRule,
  pageFollowUpApplications,
  pageFollowUpRules,
  scanFollowUpRules,
  updateFollowUpRule
} from "../../api/followUpAgent";
import type {
  AgentFollowUpApplicationInfo,
  AgentFollowUpApplicationQuery,
  AgentFollowUpRuleInfo,
  AgentFollowUpRuleQuery
} from "../../api/types";

const activeTab = ref("applications");
const applicationStatusOptions = ["INTERESTED", "APPLIED", "COMMUNICATING", "INTERVIEWING", "OFFER", "REJECTED"];
const ruleTypeOptions = ["INTERVIEW_SCHEDULED", "APPLICATION_NO_FEEDBACK", "INTERVIEW_BEFORE", "INTERVIEW_AFTER_REVIEW"];

const applicationQuery = reactive<AgentFollowUpApplicationQuery>({
  pageNum: 1,
  pageSize: 10,
  userId: "",
  status: "",
  keyword: "",
  failedEmailOnly: false
});
const ruleQuery = reactive<AgentFollowUpRuleQuery>({
  pageNum: 1,
  pageSize: 10,
  ruleName: "",
  ruleType: "",
  status: ""
});

const applications = ref<AgentFollowUpApplicationInfo[]>([]);
const rules = ref<AgentFollowUpRuleInfo[]>([]);
const applicationTotal = ref(0);
const ruleTotal = ref(0);
const applicationLoading = ref(false);
const ruleLoading = ref(false);
const scanLoading = ref(false);
const ruleDialogVisible = ref(false);
const savingRule = ref(false);
const editingRuleId = ref<number | null>(null);

const ruleForm = reactive<AgentFollowUpRuleInfo>(defaultRuleForm());

const emailEnabled = computed({
  get: () => ruleForm.emailEnabled === 1,
  set: value => {
    ruleForm.emailEnabled = value ? 1 : 0;
  }
});

const workflowEnabled = computed({
  get: () => ruleForm.workflowEnabled === 1,
  set: value => {
    ruleForm.workflowEnabled = value ? 1 : 0;
  }
});

onMounted(() => {
  loadApplications();
  loadRules();
});

function defaultRuleForm(): AgentFollowUpRuleInfo {
  return {
    ruleCode: "",
    ruleName: "",
    ruleType: "APPLICATION_NO_FEEDBACK",
    triggerStatus: "APPLIED",
    delayMinutes: 4320,
    reminderType: "FOLLOW_UP",
    reminderTitle: "",
    reminderTemplate: "",
    emailEnabled: 0,
    workflowEnabled: 1,
    maxRetryCount: 3,
    retryIntervalSeconds: 300,
    status: "ENABLED",
    remark: ""
  };
}

async function loadApplications() {
  applicationLoading.value = true;
  try {
    const page = await pageFollowUpApplications({ ...applicationQuery });
    applications.value = page.records || [];
    applicationTotal.value = page.total || 0;
    applicationQuery.pageNum = page.current || applicationQuery.pageNum;
    applicationQuery.pageSize = page.size || applicationQuery.pageSize;
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : "求职跟进明细加载失败");
  } finally {
    applicationLoading.value = false;
  }
}

async function loadRules() {
  ruleLoading.value = true;
  try {
    const page = await pageFollowUpRules({ ...ruleQuery });
    rules.value = page.records || [];
    ruleTotal.value = page.total || 0;
    ruleQuery.pageNum = page.current || ruleQuery.pageNum;
    ruleQuery.pageSize = page.size || ruleQuery.pageSize;
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : "自动跟进规则加载失败");
  } finally {
    ruleLoading.value = false;
  }
}

function refreshCurrent() {
  if (activeTab.value === "applications") {
    loadApplications();
  } else {
    loadRules();
  }
}

function searchApplications() {
  applicationQuery.pageNum = 1;
  loadApplications();
}

function resetApplicationQuery() {
  Object.assign(applicationQuery, { pageNum: 1, pageSize: 10, userId: "", status: "", keyword: "", failedEmailOnly: false });
  loadApplications();
}

function searchRules() {
  ruleQuery.pageNum = 1;
  loadRules();
}

function resetRuleQuery() {
  Object.assign(ruleQuery, { pageNum: 1, pageSize: 10, ruleName: "", ruleType: "", status: "" });
  loadRules();
}

function handleApplicationCurrentChange(pageNum: number) {
  applicationQuery.pageNum = pageNum;
  loadApplications();
}

function handleApplicationSizeChange(pageSize: number) {
  applicationQuery.pageSize = pageSize;
  applicationQuery.pageNum = 1;
  loadApplications();
}

function handleRuleCurrentChange(pageNum: number) {
  ruleQuery.pageNum = pageNum;
  loadRules();
}

function handleRuleSizeChange(pageSize: number) {
  ruleQuery.pageSize = pageSize;
  ruleQuery.pageNum = 1;
  loadRules();
}

function openRuleDialog(row?: AgentFollowUpRuleInfo) {
  editingRuleId.value = row?.id || null;
  Object.assign(ruleForm, row ? { ...row } : defaultRuleForm());
  ruleDialogVisible.value = true;
}

async function saveRule() {
  if (!ruleForm.ruleCode || !ruleForm.ruleName || !ruleForm.ruleType) {
    ElMessage.warning("规则编码、规则名称、规则类型不能为空");
    return;
  }
  savingRule.value = true;
  try {
    if (editingRuleId.value) {
      await updateFollowUpRule(editingRuleId.value, { ...ruleForm });
    } else {
      await createFollowUpRule({ ...ruleForm });
    }
    ElMessage.success("规则已保存");
    ruleDialogVisible.value = false;
    await loadRules();
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : "规则保存失败");
  } finally {
    savingRule.value = false;
  }
}

async function removeRule(row: AgentFollowUpRuleInfo) {
  if (!row.id) return;
  try {
    await ElMessageBox.confirm(`确认删除规则「${row.ruleName}」吗？`, "删除规则", { type: "warning" });
  } catch {
    return;
  }
  await deleteFollowUpRule(row.id);
  ElMessage.success("规则已删除");
  await loadRules();
}

async function scanRules() {
  scanLoading.value = true;
  try {
    const count = await scanFollowUpRules();
    ElMessage.success(`扫描完成，本次创建 ${count || 0} 条提醒`);
    await loadApplications();
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : "规则扫描失败");
  } finally {
    scanLoading.value = false;
  }
}
</script>

<style scoped>
.follow-page {
  display: grid;
  gap: 16px;
}

.page-header,
.filter-card,
.table-card {
  padding: 20px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #ffffff;
}

.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.page-header h1 {
  margin: 4px 0;
  color: #111827;
}

.page-header p {
  margin: 0;
  color: #6b7280;
}

.eyebrow {
  color: #2563eb;
  font-size: 13px;
  font-weight: 700;
}

.header-actions {
  display: flex;
  gap: 10px;
}

.content-tabs {
  display: grid;
  gap: 14px;
}

.filter-card.two-column {
  display: grid;
  grid-template-columns: 1fr auto;
  align-items: start;
  gap: 16px;
}

.filter-form {
  display: grid;
  grid-template-columns: repeat(4, minmax(180px, 1fr));
  gap: 12px 16px;
}

.filter-form.compact {
  grid-template-columns: repeat(4, minmax(180px, 1fr));
}

.filter-form :deep(.el-form-item) {
  margin-bottom: 0;
}

.filter-actions {
  align-items: flex-end;
}

.rule-actions {
  display: flex;
  justify-content: flex-end;
}

.pagination-row {
  margin-top: 16px;
  justify-content: flex-end;
}

.rule-form {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 4px 12px;
}

.rule-form :deep(.el-form-item:nth-child(8)),
.rule-form :deep(.el-form-item:last-child) {
  grid-column: 1 / -1;
}

@media (max-width: 1100px) {
  .page-header,
  .filter-card.two-column {
    grid-template-columns: 1fr;
  }

  .page-header {
    align-items: flex-start;
    flex-direction: column;
  }

  .filter-form,
  .filter-form.compact,
  .rule-form {
    grid-template-columns: 1fr;
  }
}
</style>
