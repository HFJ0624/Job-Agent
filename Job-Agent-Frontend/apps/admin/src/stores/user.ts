import { defineStore } from "pinia";
import { getCurrentUserApi, loginApi, logoutApi } from "../api/auth";
import type { UserInfo } from "../api/types";

const TOKEN_VALUE_KEY = "job-agent-admin-token-value";

export const useAdminUserStore = defineStore("admin-user", {
  state: () => ({
    profile: null as UserInfo | null
  }),
  getters: {
    isLogin: () => Boolean(localStorage.getItem(TOKEN_VALUE_KEY)),
    displayName: state => state.profile?.nickname || state.profile?.username || "未登录"
  },
  actions: {
    async login(payload: { account: string; password: string }) {
      const data = await loginApi({
        account: payload.account,
        password: payload.password
      });
      this.profile = data.user;
    },
    async loadProfile() {
      this.profile = await getCurrentUserApi();
    },
    async logout() {
      try {
        await logoutApi();
      } catch (error) {
        // 退出时即使后端 token 已失效，也要清空前端登录态，避免用户卡在后台页面。
        console.error("[Job-Agent Admin] 后台退出接口异常，本地登录态已清理", error);
      } finally {
        this.profile = null;
      }
    }
  }
});
