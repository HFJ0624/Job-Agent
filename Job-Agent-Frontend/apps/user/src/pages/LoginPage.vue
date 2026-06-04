<template>
  <main class="auth-page">
    <section class="auth-card">
      <p class="eyebrow">欢迎回来</p>
      <h1>登录 Job-Agent</h1>
      <p class="auth-desc">支持使用用户名、手机号或邮箱登录。</p>

      <form class="form-stack" @submit.prevent="submit">
        <label>
          <span>账号</span>
          <input v-model="form.account" required placeholder="用户名 / 手机号 / 邮箱" />
        </label>
        <label>
          <span>密码</span>
          <input v-model="form.password" required minlength="6" maxlength="32" type="password" placeholder="请输入密码" />
        </label>

        <p v-if="errorMessage" class="form-error">{{ errorMessage }}</p>
        <button class="primary-button large" :disabled="authStore.loading">登录</button>
      </form>

      <p class="auth-switch">还没有账号？<RouterLink to="/register">立即注册</RouterLink></p>
    </section>
  </main>
</template>

<script setup lang="ts">
import { reactive, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { useAuthStore } from "../stores/auth";

const router = useRouter();
const route = useRoute();
const authStore = useAuthStore();
const errorMessage = ref("");

const form = reactive({
  account: "",
  password: ""
});

async function submit() {
  errorMessage.value = "";
  try {
    await authStore.login(form);
    router.push(String(route.query.redirect || "/home"));
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : "登录失败";
  }
}
</script>
