import { request } from "./request";
import type { PageResult, PositionImportResult, PositionInfo, PositionSavePayload } from "./types";

/**
 * 查询岗位分页列表。
 * P表示参数描述，后台可以按关键词、公司、城市、类别和状态筛选。
 */
export function pagePositionsApi(params: {
  pageNo: number;
  pageSize: number;
  keyword?: string;
  companyId?: number | "";
  city?: string;
  jobCategory?: string;
  status?: number | "";
}) {
  const search = new URLSearchParams();
  search.set("pageNo", String(params.pageNo));
  search.set("pageSize", String(params.pageSize));

  if (params.keyword) search.set("keyword", params.keyword);
  if (params.companyId !== "" && params.companyId !== undefined) search.set("companyId", String(params.companyId));
  if (params.city) search.set("city", params.city);
  if (params.jobCategory) search.set("jobCategory", params.jobCategory);
  if (params.status !== "" && params.status !== undefined) search.set("status", String(params.status));

  return request<PageResult<PositionInfo>>(`/admin/job/page?${search.toString()}`);
}

/**
 * 新增岗位。
 */
export function createPositionApi(payload: PositionSavePayload) {
  return request<PositionInfo>("/admin/job", {
    method: "POST",
    body: JSON.stringify(payload)
  });
}

/**
 * 修改岗位。
 */
export function updatePositionApi(positionId: number, payload: PositionSavePayload) {
  return request<PositionInfo>(`/admin/job/${positionId}`, {
    method: "PUT",
    body: JSON.stringify(payload)
  });
}

/**
 * 逻辑删除岗位。
 */
export function deletePositionApi(positionId: number) {
  return request<void>(`/admin/job/${positionId}`, {
    method: "DELETE"
  });
}

/**
 * 发布岗位。
 * P表示参数描述，发布后 status=1，前台用户才能看到。
 */
export function publishPositionApi(positionId: number) {
  return request<PositionInfo>(`/admin/job/${positionId}/publish`, {
    method: "PUT"
  });
}

/**
 * 下架岗位。
 * P表示参数描述，下架后 status=0，前台用户看不到。
 */
export function offlinePositionApi(positionId: number) {
  return request<PositionInfo>(`/admin/job/${positionId}/offline`, {
    method: "PUT"
  });
}

/**
 * 导入岗位 Excel。
 * P表示参数描述，字段名必须是 file，和后端 @RequestPart("file") 保持一致。
 */
export function importPositionsApi(file: File) {
  const formData = new FormData();
  formData.append("file", file);

  return request<PositionImportResult>("/admin/job/import", {
    method: "POST",
    body: formData
  });
}
