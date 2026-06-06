<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import { useRouter } from "vue-router";
import { ElMessage, ElMessageBox } from "element-plus";
import {
  createCompanyApi,
  deleteCompanyApi,
  pageCompaniesApi,
  updateCompanyApi
} from "../../api/company";
import type { CompanyInfo, CompanySavePayload } from "../../api/types";

const router = useRouter();
const loading = ref(false);
const saving = ref(false);
const companies = ref<CompanyInfo[]>([]);
const total = ref(0);
const errorMessage = ref("");
const dialogVisible = ref(false);
const editingCompanyId = ref<number | null>(null);

const query = reactive({
  pageNo: 1,
  pageSize: 10,
  keyword: "",
  status: "" as number | ""
});

const companyForm = reactive<CompanySavePayload>(createEmptyForm());

function createEmptyForm(): CompanySavePayload {
  // 1. 把表单默认值集中放在这里，新增、取消、保存成功后都能复用同一套重置逻辑。
  return {
    companyName: "",
    logoUrl: "",
    industry: "",
    companySize: "",
    financingStage: "",
    description: "",
    province: "",
    city: "",
    district: "",
    address: "",
    longitude: null,
    latitude: null,
    prospectScore: null,
    status: 1
  };
}

async function loadCompanies() {
  loading.value = true;
  errorMessage.value = "";
  try {
    const page = await pageCompaniesApi(query);
    companies.value = page.records;
    total.value = page.total;
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : "公司列表加载失败";
  } finally {
    loading.value = false;
  }
}

function search() {
  // 1. 重新筛选时回到第一页，避免当前页码超过筛选后的总页数。
  query.pageNo = 1;
  loadCompanies();
}

function openCreateDialog() {
  editingCompanyId.value = null;
  Object.assign(companyForm, createEmptyForm());
  dialogVisible.value = true;
}

function openEditDialog(company: CompanyInfo) {
  editingCompanyId.value = company.id;

  // 1. 只把表单允许编辑的字段放进表单，避免误把 createTime 等展示字段提交回后端。
  Object.assign(companyForm, {
    companyName: company.companyName || "",
    logoUrl: company.logoUrl || "",
    industry: company.industry || "",
    companySize: company.companySize || "",
    financingStage: company.financingStage || "",
    description: company.description || "",
    province: company.province || "",
    city: company.city || "",
    district: company.district || "",
    address: company.address || "",
    longitude: company.longitude ?? null,
    latitude: company.latitude ?? null,
    prospectScore: company.prospectScore ?? null,
    status: company.status ?? 1
  });
  dialogVisible.value = true;
}

async function saveCompany() {
  const payload = buildCompanyPayload();
  if (!payload.companyName) {
    ElMessage.warning("请填写公司名称");
    return;
  }

  saving.value = true;
  try {
    if (editingCompanyId.value) {
      await updateCompanyApi(editingCompanyId.value, payload);
      ElMessage.success("公司信息已更新");
    } else {
      await createCompanyApi(payload);
      ElMessage.success("公司已新增");
    }

    dialogVisible.value = false;
    await loadCompanies();
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : "公司保存失败");
  } finally {
    saving.value = false;
  }
}

function buildCompanyPayload(): CompanySavePayload {
  // 1. 提交前统一 trim，避免数据库里保存一堆首尾空格。
  return {
    companyName: companyForm.companyName.trim(),
    logoUrl: trimToUndefined(companyForm.logoUrl),
    industry: trimToUndefined(companyForm.industry),
    companySize: trimToUndefined(companyForm.companySize),
    financingStage: trimToUndefined(companyForm.financingStage),
    description: trimToUndefined(companyForm.description),
    province: trimToUndefined(companyForm.province),
    city: trimToUndefined(companyForm.city),
    district: trimToUndefined(companyForm.district),
    address: trimToUndefined(companyForm.address),
    longitude: companyForm.longitude ?? null,
    latitude: companyForm.latitude ?? null,
    prospectScore: companyForm.prospectScore ?? null,
    status: companyForm.status ?? 1
  };
}

async function removeCompany(company: CompanyInfo) {
  try {
    await ElMessageBox.confirm(`确定删除「${company.companyName}」吗？删除后列表中将不再显示。`, "删除公司", {
      type: "warning",
      confirmButtonText: "删除",
      cancelButtonText: "取消"
    });

    // 1. 后端执行逻辑删除，不会真正物理删除数据库记录。
    await deleteCompanyApi(company.id);
    ElMessage.success("公司已删除");
    await loadCompanies();
  } catch (error) {
    if (error === "cancel" || error === "close") {
      return;
    }
    ElMessage.error(error instanceof Error ? error.message : "公司删除失败");
  }
}

function goImport() {
  router.push("/company/import");
}

function formatArea(company: CompanyInfo) {
  return [company.province, company.city, company.district].filter(Boolean).join(" / ") || "-";
}

function trimToUndefined(value?: string) {
  const text = value?.trim();
  return text || undefined;
}

onMounted(loadCompanies);
</script>

<template>
  <el-card shadow="never">
    <template #header>
      <div class="card-header">
        <span>公司列表</span>
        <div class="table-tools company-tools">
          <el-input
            v-model="query.keyword"
            clearable
            placeholder="公司名称 / 行业 / 城市 / 地址"
            @keyup.enter="search"
          />
          <el-select v-model="query.status" clearable placeholder="状态">
            <el-option label="正常" :value="1" />
            <el-option label="禁用" :value="0" />
          </el-select>
          <el-button type="primary" @click="search">查询</el-button>
          <el-button @click="goImport">Excel导入</el-button>
          <el-button type="success" @click="openCreateDialog">新增公司</el-button>
        </div>
      </div>
    </template>

    <el-alert v-if="errorMessage" :title="errorMessage" type="error" show-icon :closable="false" class="table-alert" />

    <el-table v-loading="loading" :data="companies" border>
      <el-table-column label="公司" min-width="230" fixed>
        <template #default="{ row }">
          <div class="company-cell">
            <el-avatar shape="square" :size="44" :src="row.logoUrl">
              {{ row.companyName?.slice(0, 1) || "企" }}
            </el-avatar>
            <div>
              <strong>{{ row.companyName }}</strong>
              <span>{{ row.industry || "未填写行业" }}</span>
            </div>
          </div>
        </template>
      </el-table-column>
      <el-table-column prop="companySize" label="规模" width="130" />
      <el-table-column prop="financingStage" label="融资阶段" width="120" />
      <el-table-column label="地区" min-width="180">
        <template #default="{ row }">{{ formatArea(row) }}</template>
      </el-table-column>
      <el-table-column prop="address" label="地址" min-width="220" show-overflow-tooltip />
      <el-table-column prop="prospectScore" label="前景分" width="100" />
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'danger'">
            {{ row.status === 1 ? "正常" : "禁用" }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="创建时间" width="170" />
      <el-table-column label="操作" width="170" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEditDialog(row)">编辑</el-button>
          <el-button link type="danger" @click="removeCompany(row)">删除</el-button>
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
      @current-change="loadCompanies"
      @size-change="search"
    />
  </el-card>

  <el-dialog
    v-model="dialogVisible"
    :title="editingCompanyId ? '编辑公司' : '新增公司'"
    width="760px"
    destroy-on-close
  >
    <el-form label-position="top" class="company-form-grid">
      <el-form-item label="公司名称" required>
        <el-input v-model="companyForm.companyName" maxlength="255" placeholder="请输入公司名称" />
      </el-form-item>
      <el-form-item label="Logo地址">
        <el-input v-model="companyForm.logoUrl" maxlength="512" placeholder="https://example.com/logo.png" />
      </el-form-item>
      <el-form-item label="行业">
        <el-input v-model="companyForm.industry" maxlength="128" placeholder="例如 互联网" />
      </el-form-item>
      <el-form-item label="公司规模">
        <el-input v-model="companyForm.companySize" maxlength="64" placeholder="例如 1000-9999人" />
      </el-form-item>
      <el-form-item label="融资阶段">
        <el-input v-model="companyForm.financingStage" maxlength="64" placeholder="例如 已上市" />
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="companyForm.status" class="full-width">
          <el-option label="正常" :value="1" />
          <el-option label="禁用" :value="0" />
        </el-select>
      </el-form-item>
      <el-form-item label="省份">
        <el-input v-model="companyForm.province" maxlength="64" placeholder="例如 广东省" />
      </el-form-item>
      <el-form-item label="城市">
        <el-input v-model="companyForm.city" maxlength="64" placeholder="例如 深圳市" />
      </el-form-item>
      <el-form-item label="区县">
        <el-input v-model="companyForm.district" maxlength="64" placeholder="例如 南山区" />
      </el-form-item>
      <el-form-item label="经度">
        <el-input-number v-model="companyForm.longitude" class="full-width" :precision="6" controls-position="right" />
      </el-form-item>
      <el-form-item label="纬度">
        <el-input-number v-model="companyForm.latitude" class="full-width" :precision="6" controls-position="right" />
      </el-form-item>
      <el-form-item label="发展前景分">
        <el-input-number v-model="companyForm.prospectScore" class="full-width" :min="0" :max="10" :precision="2" controls-position="right" />
      </el-form-item>
      <el-form-item label="详细地址" class="company-form-full">
        <el-input v-model="companyForm.address" maxlength="255" placeholder="请输入公司详细地址" />
      </el-form-item>
      <el-form-item label="公司简介" class="company-form-full">
        <el-input v-model="companyForm.description" type="textarea" :rows="4" placeholder="请输入公司简介" />
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="dialogVisible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="saveCompany">保存</el-button>
    </template>
  </el-dialog>
</template>
