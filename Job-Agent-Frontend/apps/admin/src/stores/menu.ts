import { defineStore } from "pinia";
import { fetchAdminMenus } from "../api/menu";
import type { AdminMenuItem, AdminUserProfile } from "../types/menu";

export const useAdminMenuStore = defineStore("admin-menu", {
  state: () => ({
    menus: [] as AdminMenuItem[],
    loaded: false
  }),
  actions: {
    async loadMenus(role: AdminUserProfile["role"]) {
      // 菜单从接口返回，路由再根据菜单动态注册。
      this.menus = await fetchAdminMenus(role);
      this.loaded = true;
    },
    reset() {
      this.menus = [];
      this.loaded = false;
    }
  }
});
