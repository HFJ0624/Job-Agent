import { request } from "./request";
import type { PageResult, UpdateProfilePayload, UserInfo } from "./types";

export function updateProfile(payload: UpdateProfilePayload) {
  return request<UserInfo>("/api/user/profile", {
    method: "PUT",
    body: JSON.stringify(payload)
  });
}

export function pageUsers(params: { pageNo?: number; pageSize?: number; keyword?: string }) {
  const search = new URLSearchParams();
  search.set("pageNo", String(params.pageNo || 1));
  search.set("pageSize", String(params.pageSize || 10));
  if (params.keyword) {
    search.set("keyword", params.keyword);
  }
  return request<PageResult<UserInfo>>(`/api/user/page?${search.toString()}`);
}
