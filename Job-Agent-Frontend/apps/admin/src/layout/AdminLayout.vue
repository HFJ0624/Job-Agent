<script setup lang="ts">
import { computed, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { Fold, SwitchButton } from "@element-plus/icons-vue";
import { adminMenus } from "../api/menu";
import SidebarMenu from "../components/SidebarMenu.vue";
import { useAdminUserStore } from "../stores/user";

const route = useRoute();
const router = useRouter();
const userStore = useAdminUserStore();
const collapsed = ref(false);

const currentTitle = computed(() => String(route.meta.title || "工作台"));

async function logout() {
  // 先调用后台退出接口，再跳回登录页；即使接口失败，请求封装也会清掉本地 token。
  await userStore.logout();
  router.replace("/login");
}
</script>

<template>
  <el-container class="admin-shell">
    <el-aside :width="collapsed ? '72px' : '236px'" class="admin-aside">
      <div class="admin-logo" :class="{ collapsed }">
        <span class="logo-mark">J</span>
        <strong v-if="!collapsed">Job Admin</strong>
      </div>

      <el-menu
        router
        :collapse="collapsed"
        :default-active="route.path"
        class="admin-menu"
        background-color="#0f172a"
        text-color="#cbd5e1"
        active-text-color="#ffffff"
      >
        <SidebarMenu :menus="adminMenus" />
      </el-menu>
    </el-aside>

    <el-container>
      <el-header class="admin-header">
        <div class="header-left">
          <el-button :icon="Fold" circle @click="collapsed = !collapsed" />
          <div>
            <div class="breadcrumb">Job-Agent / {{ currentTitle }}</div>
            <h1>{{ currentTitle }}</h1>
          </div>
        </div>

        <div class="header-right">
          <el-tag effect="plain" type="success">固定菜单</el-tag>
          <span>{{ userStore.displayName }}</span>
          <el-button :icon="SwitchButton" circle @click="logout" />
        </div>
      </el-header>

      <el-main class="admin-main">
        <RouterView />
      </el-main>
    </el-container>
  </el-container>
</template>
