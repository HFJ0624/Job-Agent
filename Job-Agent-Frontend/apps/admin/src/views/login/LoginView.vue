<script setup lang="ts">
import { reactive, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import type { AdminUserProfile } from "../../types/menu";
import { useAdminMenuStore } from "../../stores/menu";
import { useAdminUserStore } from "../../stores/user";

const router = useRouter();
const route = useRoute();
const userStore = useAdminUserStore();
const menuStore = useAdminMenuStore();
const errorMessage = ref("");

const form = reactive({
  account: "",
  password: "",
  role: "admin" as AdminUserProfile["role"]
});

async function submit() {
  errorMessage.value = "";
  try {
    // 后端负责账号密码和 token，role 只用于当前后台的动态菜单演示。
    menuStore.reset();
    await userStore.login(form);
    router.replace(String(route.query.redirect || "/dashboard"));
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : "登录失败";
  }
}
</script>

<template>
  <main class="login-page">
    <section class="login-card">
      <p class="login-eyebrow">vue-pure-admin style</p>
      <h1>Job-Agent 管理后台</h1>
      <p class="login-desc">账号密码走后端登录接口，菜单角色用于演示动态菜单权限。</p>

      <el-form label-position="top" @submit.prevent>
        <el-form-item label="账号">
          <el-input v-model="form.account" placeholder="用户名 / 手机号 / 邮箱" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="form.password" type="password" show-password placeholder="请输入密码" />
        </el-form-item>
        <el-form-item label="菜单角色">
          <el-select v-model="form.role" class="full-width">
            <el-option label="超级管理员：全量菜单" value="admin" />
            <el-option label="运营管理员：运营菜单" value="operator" />
          </el-select>
        </el-form-item>

        <el-alert v-if="errorMessage" :title="errorMessage" type="error" show-icon :closable="false" />
        <el-button type="primary" size="large" class="full-width login-submit" @click="submit">登录后台</el-button>
      </el-form>
    </section>
  </main>
</template>
