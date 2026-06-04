import { defineStore } from "pinia";
import { adminMenus } from "../api/menu";
import type { AdminMenuItem } from "../types/menu";

export const useAdminMenuStore = defineStore("admin-menu", {
  state: () => ({
    menus: adminMenus as AdminMenuItem[]
  })
});
