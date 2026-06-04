import { clearToken, request, saveToken } from "./request";
import type { LoginResponse, UserInfo } from "./types";

export async function loginApi(payload: { account: string; password: string }) {
  const data = await request<LoginResponse>("/admin/auth/login", {
    method: "POST",
    body: JSON.stringify(payload)
  });
  saveToken(data.tokenName, data.tokenValue);
  return data;
}

export async function logoutApi() {
  try {
    await request<null>("/admin/auth/logout", {
      method: "POST"
    });
  } finally {
    clearToken();
  }
}

export function getCurrentUserApi() {
  return request<UserInfo>("/admin/auth/me");
}
