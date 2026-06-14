import { request } from "./request";
import type {
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
