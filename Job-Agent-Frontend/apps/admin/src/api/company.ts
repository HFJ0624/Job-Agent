import { request } from "./request";
import type { CompanyImportResult, CompanyInfo, CompanySavePayload, PageResult } from "./types";

/**
 * 查询公司分页列表。
 * P表示参数描述，keyword 和 status 都是可选筛选条件。
 */
export function pageCompaniesApi(params: { pageNo: number; pageSize: number; keyword?: string; status?: number | "" }) {
  const search = new URLSearchParams();
  search.set("pageNo", String(params.pageNo));
  search.set("pageSize", String(params.pageSize));

  if (params.keyword) {
    search.set("keyword", params.keyword);
  }
  if (params.status !== "" && params.status !== undefined) {
    search.set("status", String(params.status));
  }

  return request<PageResult<CompanyInfo>>(`/admin/company/page?${search.toString()}`);
}

/**
 * 新增公司。
 */
export function createCompanyApi(payload: CompanySavePayload) {
  return request<CompanyInfo>("/admin/company", {
    method: "POST",
    body: JSON.stringify(payload)
  });
}

/**
 * 修改公司。
 */
export function updateCompanyApi(companyId: number, payload: CompanySavePayload) {
  return request<CompanyInfo>(`/admin/company/${companyId}`, {
    method: "PUT",
    body: JSON.stringify(payload)
  });
}

/**
 * 逻辑删除公司。
 */
export function deleteCompanyApi(companyId: number) {
  return request<void>(`/admin/company/${companyId}`, {
    method: "DELETE"
  });
}

/**
 * 导入公司 Excel。
 * P表示参数描述，字段名必须是 file，和后端 @RequestPart("file") 保持一致。
 */
export function importCompaniesApi(file: File) {
  const formData = new FormData();
  formData.append("file", file);

  return request<CompanyImportResult>("/admin/company/import", {
    method: "POST",
    body: formData
  });
}
