<script setup lang="ts">
import { reactive, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { useAdminUserStore } from "../../stores/user";

const router = useRouter();
const route = useRoute();
const userStore = useAdminUserStore();
const errorMessage = ref("");

const form = reactive({
  account: "",
  password: ""
});

async function submit() {
  errorMessage.value = "";
  try {
    // 后台登录只提交账号和密码，接口固定走 /admin/auth/login。
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
      <p class="login-desc">后台登录接口独立走 /admin/auth/login，和用户前台登录完全分开。</p>

      <el-form label-position="top" @submit.prevent>
        <el-form-item label="账号">
          <el-input v-model="form.account" placeholder="用户名 / 手机号 / 邮箱" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="form.password" type="password" show-password placeholder="请输入密码" />
        </el-form-item>

        <el-alert v-if="errorMessage" :title="errorMessage" type="error" show-icon :closable="false" />
        <el-button type="primary" size="large" class="full-width login-submit" @click="submit">登录后台</el-button>
      </el-form>
    </section>
  </main>
</template>
