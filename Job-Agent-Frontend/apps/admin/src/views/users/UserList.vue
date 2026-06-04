<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import { pageUsersApi } from "../../api/user";
import type { UserInfo } from "../../api/types";

const loading = ref(false);
const users = ref<UserInfo[]>([]);
const total = ref(0);
const errorMessage = ref("");

const query = reactive({
  pageNo: 1,
  pageSize: 10,
  keyword: ""
});

async function loadUsers() {
  loading.value = true;
  errorMessage.value = "";
  try {
    const page = await pageUsersApi(query);
    users.value = page.records;
    total.value = page.total;
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : "用户列表加载失败";
  } finally {
    loading.value = false;
  }
}

function search() {
  query.pageNo = 1;
  loadUsers();
}

onMounted(loadUsers);
</script>

<template>
  <el-card shadow="never">
    <template #header>
      <div class="card-header">
        <span>用户列表</span>
        <div class="table-tools">
          <el-input v-model="query.keyword" clearable placeholder="用户名 / 昵称 / 手机 / 邮箱" @keyup.enter="search" />
          <el-button type="primary" @click="search">查询</el-button>
        </div>
      </div>
    </template>

    <el-alert v-if="errorMessage" :title="errorMessage" type="error" show-icon :closable="false" class="table-alert" />

    <el-table v-loading="loading" :data="users" border>
      <el-table-column prop="id" label="ID" width="180" />
      <el-table-column prop="username" label="用户名" />
      <el-table-column prop="nickname" label="昵称" />
      <el-table-column prop="phone" label="手机号" />
      <el-table-column prop="email" label="邮箱" min-width="180" />
      <el-table-column prop="education" label="学历" />
      <el-table-column prop="workYears" label="工作年限" />
      <el-table-column label="状态">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'danger'">
            {{ row.status === 1 ? "正常" : "禁用" }}
          </el-tag>
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
      @current-change="loadUsers"
      @size-change="search"
    />
  </el-card>
</template>
