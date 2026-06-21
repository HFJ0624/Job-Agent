import { request } from "./request";
import type {
  AiPromptTemplateInfo,
  AiPromptTemplateQuery,
  AiPromptVersionInfo,
  PageResult
} from "./types";

/**
 * 分页查询 Prompt 模板。
 */
export function pagePromptTemplates(query: AiPromptTemplateQuery) {
  return request<PageResult<AiPromptTemplateInfo>>(`/admin/ai/prompts/templates/page?${toQueryString(query)}`);
}

/**
 * 新增 Prompt 模板。
 */
export function createPromptTemplate(payload: AiPromptTemplateInfo) {
  return request<AiPromptTemplateInfo>("/admin/ai/prompts/templates", {
    method: "POST",
    body: JSON.stringify(payload)
  });
}

/**
 * 修改 Prompt 模板。
 */
export function updatePromptTemplate(id: number, payload: AiPromptTemplateInfo) {
  return request<AiPromptTemplateInfo>(`/admin/ai/prompts/templates/${id}`, {
    method: "PUT",
    body: JSON.stringify(payload)
  });
}

/**
 * 删除 Prompt 模板。
 */
export function deletePromptTemplate(id: number) {
  return request<void>(`/admin/ai/prompts/templates/${id}`, {
    method: "DELETE"
  });
}

/**
 * 查询模板下的 Prompt 版本。
 */
export function listPromptVersions(templateId: number) {
  return request<AiPromptVersionInfo[]>(`/admin/ai/prompts/templates/${templateId}/versions`);
}

/**
 * 新增 Prompt 版本。
 */
export function createPromptVersion(payload: AiPromptVersionInfo) {
  return request<AiPromptVersionInfo>("/admin/ai/prompts/versions", {
    method: "POST",
    body: JSON.stringify(payload)
  });
}

/**
 * 修改 Prompt 版本。
 */
export function updatePromptVersion(id: number, payload: AiPromptVersionInfo) {
  return request<AiPromptVersionInfo>(`/admin/ai/prompts/versions/${id}`, {
    method: "PUT",
    body: JSON.stringify(payload)
  });
}

/**
 * 发布 Prompt 版本。
 */
export function publishPromptVersion(id: number) {
  return request<AiPromptVersionInfo>(`/admin/ai/prompts/versions/${id}/publish`, {
    method: "POST"
  });
}

/**
 * 归档 Prompt 版本。
 */
export function archivePromptVersion(id: number) {
  return request<AiPromptVersionInfo>(`/admin/ai/prompts/versions/${id}/archive`, {
    method: "POST"
  });
}

function toQueryString(query: object) {
  const params = new URLSearchParams();

  Object.entries(query as Record<string, unknown>).forEach(([key, value]) => {
    if (value === undefined || value === null || value === "") {
      return;
    }
    params.set(key, String(value));
  });

  return params.toString();
}
