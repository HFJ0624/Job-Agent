<script setup lang="ts">
import { computed, onMounted } from "vue";
import { useRouter } from "vue-router";
import { useAuthStore } from "./stores/auth";
import { avatarInitial, normalizeAvatarUrl } from "./utils/avatar";

const router = useRouter();
const authStore = useAuthStore();
const navAvatarUrl = computed(() => normalizeAvatarUrl(authStore.user?.avatarUrl));
const navAvatarText = computed(() => avatarInitial(authStore.displayName));

onMounted(() => {
  // 页面刷新后尝试用本地 token 拉取当前用户。
  authStore.loadMe();
});

async function handleLogout() {
  await authStore.logout();
  router.push("/home");
}
</script>

<template>
  <div class="user-shell">
    <header class="top-bar">
      <RouterLink class="brand" to="/home">
        <span class="brand-mark">J</span>
        <span>Job-Agent</span>
      </RouterLink>

      <nav class="nav-links" aria-label="主导航">
        <RouterLink to="/home">首页</RouterLink>
        <RouterLink to="/jobs">职位</RouterLink>
        <RouterLink to="/resume">我的简历</RouterLink>
        <RouterLink to="/agent">AI 助手</RouterLink>
        <RouterLink to="/ai-interview">AI 面试</RouterLink>
        <RouterLink to="/job-recommend">智能推荐</RouterLink>
        <RouterLink to="/application">求职进度</RouterLink>
        <RouterLink to="/communication">沟通记录</RouterLink>
      </nav>

      <div class="top-actions">
        <template v-if="authStore.isLogin">
          <RouterLink class="user-entry" to="/profile">
            <span class="nav-avatar">
              <img v-if="navAvatarUrl" :src="navAvatarUrl" alt="用户头像" />
              <span v-else>{{ navAvatarText }}</span>
            </span>
            <span>{{ authStore.displayName }}</span>
          </RouterLink>
          <button class="primary-button" @click="handleLogout">退出</button>
        </template>
        <template v-else>
          <RouterLink class="text-button" to="/login">登录</RouterLink>
          <RouterLink class="primary-button" to="/register">注册</RouterLink>
        </template>
      </div>
    </header>

    <RouterView />
  </div>
</template>
