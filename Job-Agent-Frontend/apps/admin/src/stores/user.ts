import { defineStore } from "pinia";
import { getCurrentUserApi, loginApi, logoutApi } from "../api/auth";
import type { UserInfo } from "../api/types";
import type { AdminUserProfile } from "../types/menu";

const ROLE_KEY = "job-agent-admin-role";
const TOKEN_VALUE_KEY = "job-agent-admin-token-value";

export const useAdminUserStore = defineStore("admin-user", {
  state: () => ({
    role: (localStorage.getItem(ROLE_KEY) || "admin") as AdminUserProfile["role"],
    profile: null as UserInfo | null
  }),
  getters: {
    isLogin: () => Boolean(localStorage.getItem(TOKEN_VALUE_KEY)),
    displayName: state => state.profile?.nickname || state.profile?.username || "未登录"
  },
  actions: {
    async login(payload: { account: string; password: string; role: AdminUserProfile["role"] }) {
      const data = await loginApi({
        account: payload.account,
        password: payload.password
      });
      this.role = payload.role;
      localStorage.setItem(ROLE_KEY, payload.role);
      this.profile = data.user;
    },
    async loadProfile() {
      this.profile = await getCurrentUserApi();
    },
    async logout() {
      await logoutApi();
      this.profile = null;
      localStorage.removeItem(ROLE_KEY);
    }
  }
});
