import { request } from "./request";
import type {
  PageResult,
  RagChunkInfo,
  RagChunkQuery,
  RagDocumentInfo,
  RagDocumentQuery,
  RagIndexResult,
  RagSearchResult,
  RagStats
} from "./types";

/**
 * 查询 RAG 知识库统计。
 */
export function getRagStats() {
  return request<RagStats>("/admin/rag/stats");
}

/**
 * 后台一键重建全部 RAG 知识。
 */
export function rebuildAllRagKnowledge() {
  return request<RagIndexResult>("/admin/rag/index/all", {
    method: "POST"
  });
}

/**
 * 后台重建公共岗位和公司知识。
 */
export function rebuildPublicRagKnowledge() {
  return request<RagIndexResult>("/admin/rag/index/public", {
    method: "POST"
  });
}

/**
 * 后台重建指定用户的私有知识。
 *
 * @param userId 普通用户 ID
 */
export function rebuildUserRagKnowledge(userId: number) {
  return request<RagIndexResult>(`/admin/rag/index/users/${userId}`, {
    method: "POST"
  });
}

/**
 * 后台增量重建单个 RAG 业务文档。
 *
 * @param userId 用户 ID，公共知识传 0
 * @param documentType 文档类型
 * @param businessId 来源业务 ID
 */
export function indexRagDocument(userId: number, documentType: string, businessId: number) {
  const params = new URLSearchParams();
  params.set("userId", String(userId));
  params.set("documentType", documentType);
  params.set("businessId", String(businessId));

  return request<RagIndexResult>(`/admin/rag/index/document?${params.toString()}`, {
    method: "POST"
  });
}

/**
 * 后台删除同步单个 RAG 业务文档。
 *
 * @param userId 用户 ID，公共知识传 0
 * @param documentType 文档类型
 * @param businessId 来源业务 ID
 */
export function deleteRagDocument(userId: number, documentType: string, businessId: number) {
  const params = new URLSearchParams();
  params.set("userId", String(userId));
  params.set("documentType", documentType);
  params.set("businessId", String(businessId));

  return request<RagIndexResult>(`/admin/rag/index/document?${params.toString()}`, {
    method: "DELETE"
  });
}

/**
 * 后台预览 RAG 检索效果。
 *
 * @param userId 用户 ID，0 表示只检索公共知识
 * @param query 检索问题
 * @param limit 召回条数
 */
export function searchRagKnowledge(userId: number, query: string, limit?: number) {
  const params = new URLSearchParams();
  params.set("userId", String(userId));
  params.set("query", query);

  if (limit) {
    params.set("limit", String(limit));
  }

  return request<RagSearchResult[]>(`/admin/rag/search?${params.toString()}`);
}

/**
 * 后台分页查看 RAG 文档。
 *
 * @param query 查询条件
 */
export function pageRagDocuments(query: RagDocumentQuery) {
  return request<PageResult<RagDocumentInfo>>(`/admin/rag/documents/page?${toQueryString(query)}`);
}

/**
 * 后台分页查看 RAG 切片。
 *
 * @param query 查询条件
 */
export function pageRagChunks(query: RagChunkQuery) {
  return request<PageResult<RagChunkInfo>>(`/admin/rag/chunks/page?${toQueryString(query)}`);
}

/**
 * 后台查看 RAG 切片详情。
 *
 * @param id 切片 ID
 */
export function getRagChunkDetail(id: number) {
  return request<RagChunkInfo>(`/admin/rag/chunks/${id}`);
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
