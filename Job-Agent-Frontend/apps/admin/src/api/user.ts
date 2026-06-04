import { request } from "./request";
import type { PageResult, UserInfo } from "./types";

export function pageUsersApi(params: { pageNo: number; pageSize: number; keyword?: string }) {
  const search = new URLSearchParams();
  search.set("pageNo", String(params.pageNo));
  search.set("pageSize", String(params.pageSize));
  if (params.keyword) {
    search.set("keyword", params.keyword);
  }
  return request<PageResult<UserInfo>>(`/admin/user/page?${search.toString()}`);
}
