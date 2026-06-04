<script setup lang="ts">
import type { Component } from "vue";
import {
  ChatLineRound,
  Connection,
  Cpu,
  DataBoard,
  Document,
  Setting,
  Tickets,
  Upload,
  User,
  UserFilled
} from "@element-plus/icons-vue";
import type { AdminMenuItem } from "../types/menu";

defineProps<{
  menus: AdminMenuItem[];
}>();

const iconMap: Record<string, Component> = {
  ChatLineRound,
  Connection,
  Cpu,
  DataBoard,
  Document,
  Setting,
  Tickets,
  Upload,
  User,
  UserFilled,
  Briefcase: Tickets
};

function resolveIcon(icon: string) {
  return iconMap[icon] || Document;
}
</script>

<template>
  <template v-for="item in menus" :key="item.id">
    <el-sub-menu v-if="item.children?.length" :index="item.path">
      <template #title>
        <el-icon>
          <component :is="resolveIcon(item.icon)" />
        </el-icon>
        <span>{{ item.title }}</span>
      </template>
      <SidebarMenu :menus="item.children" />
    </el-sub-menu>

    <el-menu-item v-else :index="item.path">
      <el-icon>
        <component :is="resolveIcon(item.icon)" />
      </el-icon>
      <span>{{ item.title }}</span>
    </el-menu-item>
  </template>
</template>
