import { defineStore } from "pinia";
import { getCurrentUser, login, logout, register } from "../api/auth";
import type { LoginPayload, RegisterPayload, UserInfo } from "../api/types";

const USER_INFO_KEY = "job-agent-user-info";

function readStoredUser() {
  const rawUser = localStorage.getItem(USER_INFO_KEY);
  if (!rawUser) {
    return null;
  }

  try {
    return JSON.parse(rawUser) as UserInfo;
  } catch {
    localStorage.removeItem(USER_INFO_KEY);
    return null;
  }
}

function saveStoredUser(user: UserInfo | null) {
  if (!user) {
    localStorage.removeItem(USER_INFO_KEY);
    return;
  }
  localStorage.setItem(USER_INFO_KEY, JSON.stringify(user));
}

export const useAuthStore = defineStore("auth", {
  state: () => ({
    user: readStoredUser(),
    loading: false
  }),
  getters: {
    isLogin: state => Boolean(state.user),
    displayName: state => state.user?.nickname || state.user?.username || "未登录"
  },
  actions: {
    setUser(user: UserInfo | null) {
      this.user = user;
      saveStoredUser(user);
    },
    async login(payload: LoginPayload) {
      this.loading = true;
      try {
        const data = await login(payload);
        this.setUser(data.user);
      } finally {
        this.loading = false;
      }
    },
    async register(payload: RegisterPayload) {
      this.loading = true;
      try {
        return await register(payload);
      } finally {
        this.loading = false;
      }
    },
    async loadMe() {
      try {
        this.setUser(await getCurrentUser());
      } catch {
        this.setUser(null);
      }
    },
    async logout() {
      await logout();
      this.setUser(null);
    }
  }
});
