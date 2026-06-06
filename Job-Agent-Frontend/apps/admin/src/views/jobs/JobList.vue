<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import { useRouter } from "vue-router";
import { ElMessage, ElMessageBox } from "element-plus";
import { pageCompaniesApi } from "../../api/company";
import {
  createPositionApi,
  deletePositionApi,
  offlinePositionApi,
  pagePositionsApi,
  publishPositionApi,
  updatePositionApi
} from "../../api/job";
import type { CompanyInfo, PositionInfo, PositionSavePayload } from "../../api/types";

const router = useRouter();
const loading = ref(false);
const saving = ref(false);
const companies = ref<CompanyInfo[]>([]);
const positions = ref<PositionInfo[]>([]);
const total = ref(0);
const errorMessage = ref("");
const dialogVisible = ref(false);
const editingPositionId = ref<number | null>(null);

const query = reactive({
  pageNo: 1,
  pageSize: 10,
  keyword: "",
  companyId: "" as number | "",
  city: "",
  jobCategory: "",
  status: "" as number | ""
});

const positionForm = reactive<PositionSavePayload>(createEmptyForm());

function createEmptyForm(): PositionSavePayload {
  // 1. 表单默认值集中放在这里，新增、取消和保存成功后都复用同一套重置逻辑。
  return {
    companyId: null,
    jobTitle: "",
    jobCategory: "",
    city: "",
    district: "",
    minSalary: null,
    maxSalary: null,
    salaryMonths: 12,
    educationReq: "",
    experienceReq: "",
    jobDescription: "",
    jobRequirement: "",
    skillKeywords: "",
    workType: "全职",
    welfareTags: "",
    source: "MANUAL",
    sourceUrl: "",
    status: 0
  };
}

async function loadCompanies() {
  try {
    // 1. 岗位必须选择所属公司，这里加载正常公司作为下拉选项。
    const page = await pageCompaniesApi({ pageNo: 1, pageSize: 100, status: 1 });
    companies.value = page.records;
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : "公司下拉列表加载失败");
  }
}

async function loadPositions() {
  loading.value = true;
  errorMessage.value = "";
  try {
    const page = await pagePositionsApi(query);
    positions.value = page.records;
    total.value = page.total;
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : "岗位列表加载失败";
  } finally {
    loading.value = false;
  }
}

function search() {
  // 1. 重新筛选时回到第一页，避免当前页码超过筛选后的总页数。
  query.pageNo = 1;
  loadPositions();
}

function openCreateDialog() {
  editingPositionId.value = null;
  Object.assign(positionForm, createEmptyForm());
  dialogVisible.value = true;
}

function openEditDialog(position: PositionInfo) {
  editingPositionId.value = position.id;

  // 1. 只把允许编辑的字段放进表单，避免把 createTime 等展示字段提交回后端。
  Object.assign(positionForm, {
    companyId: position.companyId,
    jobTitle: position.jobTitle || "",
    jobCategory: position.jobCategory || "",
    city: position.city || "",
    district: position.district || "",
    minSalary: position.minSalary ?? null,
    maxSalary: position.maxSalary ?? null,
    salaryMonths: position.salaryMonths ?? 12,
    educationReq: position.educationReq || "",
    experienceReq: position.experienceReq || "",
    jobDescription: position.jobDescription || "",
    jobRequirement: position.jobRequirement || "",
    skillKeywords: position.skillKeywords || "",
    workType: position.workType || "全职",
    welfareTags: position.welfareTags || "",
    source: position.source || "MANUAL",
    sourceUrl: position.sourceUrl || "",
    status: position.status ?? 0
  });
  dialogVisible.value = true;
}

async function savePosition() {
  const payload = buildPositionPayload();
  if (!payload.companyId) {
    ElMessage.warning("请选择所属公司");
    return;
  }
  if (!payload.jobTitle) {
    ElMessage.warning("请填写岗位名称");
    return;
  }
  if (payload.minSalary && payload.maxSalary && payload.minSalary > payload.maxSalary) {
    ElMessage.warning("最低薪资不能大于最高薪资");
    return;
  }

  saving.value = true;
  try {
    if (editingPositionId.value) {
      await updatePositionApi(editingPositionId.value, payload);
      ElMessage.success("岗位信息已更新");
    } else {
      await createPositionApi(payload);
      ElMessage.success("岗位已新增");
    }

    dialogVisible.value = false;
    await loadPositions();
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : "岗位保存失败");
  } finally {
    saving.value = false;
  }
}

function buildPositionPayload(): PositionSavePayload {
  // 1. 提交前统一 trim，避免数据库里保存一堆首尾空格。
  return {
    companyId: positionForm.companyId,
    jobTitle: positionForm.jobTitle.trim(),
    jobCategory: trimToUndefined(positionForm.jobCategory),
    city: trimToUndefined(positionForm.city),
    district: trimToUndefined(positionForm.district),
    minSalary: positionForm.minSalary ?? null,
    maxSalary: positionForm.maxSalary ?? null,
    salaryMonths: positionForm.salaryMonths ?? null,
    educationReq: trimToUndefined(positionForm.educationReq),
    experienceReq: trimToUndefined(positionForm.experienceReq),
    jobDescription: trimToUndefined(positionForm.jobDescription),
    jobRequirement: trimToUndefined(positionForm.jobRequirement),
    skillKeywords: trimToUndefined(positionForm.skillKeywords),
    workType: trimToUndefined(positionForm.workType),
    welfareTags: trimToUndefined(positionForm.welfareTags),
    source: trimToUndefined(positionForm.source),
    sourceUrl: trimToUndefined(positionForm.sourceUrl),
    status: positionForm.status ?? 0
  };
}

async function togglePublish(position: PositionInfo) {
  try {
    // 1. status=1 是已发布；status=0 是草稿/下架，前台只展示已发布岗位。
    if (position.status === 1) {
      await offlinePositionApi(position.id);
      ElMessage.success("岗位已下架，前台用户将不可见");
    } else {
      await publishPositionApi(position.id);
      ElMessage.success("岗位已发布，前台用户可以看到");
    }
    await loadPositions();
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : "岗位状态更新失败");
  }
}

async function removePosition(position: PositionInfo) {
  try {
    await ElMessageBox.confirm(`确定删除「${position.jobTitle}」吗？删除后列表中将不再显示。`, "删除岗位", {
      type: "warning",
      confirmButtonText: "删除",
      cancelButtonText: "取消"
    });

    // 1. 后端执行逻辑删除，不会真正物理删除数据库记录。
    await deletePositionApi(position.id);
    ElMessage.success("岗位已删除");
    await loadPositions();
  } catch (error) {
    if (error === "cancel" || error === "close") {
      return;
    }
    ElMessage.error(error instanceof Error ? error.message : "岗位删除失败");
  }
}

function goImport() {
  router.push("/job/import");
}

function formatSalary(position: PositionInfo) {
  if (!position.minSalary && !position.maxSalary) {
    return "-";
  }
  const min = position.minSalary ? Math.round(position.minSalary / 1000) : 0;
  const max = position.maxSalary ? Math.round(position.maxSalary / 1000) : 0;
  if (min && max) return `${min}-${max}K`;
  if (min) return `${min}K起`;
  return `${max}K以内`;
}

function trimToUndefined(value?: string) {
  const text = value?.trim();
  return text || undefined;
}

onMounted(async () => {
  await Promise.all([loadCompanies(), loadPositions()]);
});
</script>

<template>
  <el-card shadow="never">
    <template #header>
      <div class="card-header">
        <span>岗位列表</span>
        <div class="table-tools job-tools">
          <el-input v-model="query.keyword" clearable placeholder="岗位 / 公司 / 城市 / 技能" @keyup.enter="search" />
          <el-select v-model="query.companyId" clearable filterable placeholder="公司">
            <el-option v-for="company in companies" :key="company.id" :label="company.companyName" :value="company.id" />
          </el-select>
          <el-select v-model="query.status" clearable placeholder="状态">
            <el-option label="已发布" :value="1" />
            <el-option label="草稿/下架" :value="0" />
          </el-select>
          <el-button type="primary" @click="search">查询</el-button>
          <el-button @click="goImport">Excel导入</el-button>
          <el-button type="success" @click="openCreateDialog">新增岗位</el-button>
        </div>
      </div>
    </template>

    <el-alert v-if="errorMessage" :title="errorMessage" type="error" show-icon :closable="false" class="table-alert" />

    <el-table v-loading="loading" :data="positions" border>
      <el-table-column label="岗位" min-width="250" fixed>
        <template #default="{ row }">
          <div class="position-cell">
            <strong>{{ row.jobTitle }}</strong>
            <span>{{ row.jobCategory || "未分类" }} · {{ row.workType || "全职" }}</span>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="公司" min-width="190">
        <template #default="{ row }">{{ row.companyName || "-" }}</template>
      </el-table-column>
      <el-table-column label="城市" width="140">
        <template #default="{ row }">{{ [row.city, row.district].filter(Boolean).join(" / ") || "-" }}</template>
      </el-table-column>
      <el-table-column label="薪资" width="110">
        <template #default="{ row }">{{ formatSalary(row) }}</template>
      </el-table-column>
      <el-table-column prop="educationReq" label="学历" width="100" />
      <el-table-column prop="experienceReq" label="经验" width="110" />
      <el-table-column prop="skillKeywords" label="技能关键词" min-width="180" show-overflow-tooltip />
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'info'">
            {{ row.status === 1 ? "已发布" : "草稿" }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="publishTime" label="发布时间" width="170" />
      <el-table-column label="操作" width="240" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEditDialog(row)">编辑</el-button>
          <el-button link :type="row.status === 1 ? 'warning' : 'success'" @click="togglePublish(row)">
            {{ row.status === 1 ? "下架" : "发布" }}
          </el-button>
          <el-button link type="danger" @click="removePosition(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      v-model:current-page="query.pageNo"
      v-model:page-size="query.pageSize"
      class="table-pagination"
      background
      layout="total, sizes, prev, pager, next"
      :total="total"
      @current-change="loadPositions"
      @size-change="search"
    />
  </el-card>

  <el-dialog v-model="dialogVisible" :title="editingPositionId ? '编辑岗位' : '新增岗位'" width="860px" destroy-on-close>
    <el-form label-position="top" class="position-form-grid">
      <el-form-item label="所属公司" required>
        <el-select v-model="positionForm.companyId" class="full-width" filterable placeholder="请选择公司">
          <el-option v-for="company in companies" :key="company.id" :label="company.companyName" :value="company.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="岗位名称" required>
        <el-input v-model="positionForm.jobTitle" maxlength="255" placeholder="例如 Java 后端开发工程师" />
      </el-form-item>
      <el-form-item label="岗位类别">
        <el-input v-model="positionForm.jobCategory" maxlength="128" placeholder="例如 后端开发" />
      </el-form-item>
      <el-form-item label="工作类型">
        <el-select v-model="positionForm.workType" class="full-width">
          <el-option label="全职" value="全职" />
          <el-option label="实习" value="实习" />
          <el-option label="远程" value="远程" />
        </el-select>
      </el-form-item>
      <el-form-item label="城市">
        <el-input v-model="positionForm.city" maxlength="64" placeholder="例如 上海" />
      </el-form-item>
      <el-form-item label="区域">
        <el-input v-model="positionForm.district" maxlength="64" placeholder="例如 徐汇区" />
      </el-form-item>
      <el-form-item label="最低薪资（元/月）">
        <el-input-number v-model="positionForm.minSalary" class="full-width" :min="0" :step="1000" controls-position="right" />
      </el-form-item>
      <el-form-item label="最高薪资（元/月）">
        <el-input-number v-model="positionForm.maxSalary" class="full-width" :min="0" :step="1000" controls-position="right" />
      </el-form-item>
      <el-form-item label="薪资月份">
        <el-input-number v-model="positionForm.salaryMonths" class="full-width" :min="1" :max="36" controls-position="right" />
      </el-form-item>
      <el-form-item label="学历要求">
        <el-input v-model="positionForm.educationReq" maxlength="64" placeholder="例如 本科" />
      </el-form-item>
      <el-form-item label="经验要求">
        <el-input v-model="positionForm.experienceReq" maxlength="64" placeholder="例如 3-5年" />
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="positionForm.status" class="full-width">
          <el-option label="草稿/下架" :value="0" />
          <el-option label="已发布" :value="1" />
        </el-select>
      </el-form-item>
      <el-form-item label="技能关键词" class="position-form-full">
        <el-input v-model="positionForm.skillKeywords" maxlength="512" placeholder="例如 Spring Boot,MySQL,Redis" />
      </el-form-item>
      <el-form-item label="福利标签" class="position-form-full">
        <el-input v-model="positionForm.welfareTags" maxlength="512" placeholder="例如 双休,年终奖,补充医疗" />
      </el-form-item>
      <el-form-item label="岗位描述" class="position-form-full">
        <el-input v-model="positionForm.jobDescription" type="textarea" :rows="4" placeholder="请输入岗位描述" />
      </el-form-item>
      <el-form-item label="岗位要求" class="position-form-full">
        <el-input v-model="positionForm.jobRequirement" type="textarea" :rows="4" placeholder="请输入岗位要求" />
      </el-form-item>
      <el-form-item label="来源" class="position-form-full">
        <el-input v-model="positionForm.source" maxlength="64" placeholder="MANUAL / IMPORT / API / CRAWLER" />
      </el-form-item>
      <el-form-item label="来源链接" class="position-form-full">
        <el-input v-model="positionForm.sourceUrl" maxlength="512" placeholder="https://example.com/job/123" />
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="dialogVisible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="savePosition">保存</el-button>
    </template>
  </el-dialog>
</template>
