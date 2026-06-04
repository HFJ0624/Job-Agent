import { clearToken, request, saveToken } from "./request";
import type { LoginPayload, LoginResponse, RegisterPayload, UserInfo } from "./types";

export async function register(payload: RegisterPayload) {
  return request<UserInfo>("/front/auth/register", {
    method: "POST",
    body: JSON.stringify(payload)
  });
}

export async function login(payload: LoginPayload) {
  const data = await request<LoginResponse>("/front/auth/login", {
    method: "POST",
    body: JSON.stringify(payload)
  });
  saveToken(data.tokenName, data.tokenValue);
  return data;
}

export async function logout() {
  try {
    await request<null>("/front/auth/logout", {
      method: "POST"
    });
  } finally {
    clearToken();
  }
}

export function getCurrentUser() {
  return request<UserInfo>("/front/auth/me");
}
