import { getToken, request } from "./request";
import type { ApiResult, ResumeInfo } from "./types";

/**
 * 上传简历。
 * P表示参数描述，resumeName 是用户给简历起的展示名称，file 是 PDF / Word 文件。
 */
export function uploadResume(payload: { resumeName: string; file: File }) {
  const formData = new FormData();
  formData.append("resumeName", payload.resumeName);
  formData.append("file", payload.file);

  // 这里 body 是 FormData，请求封装层会自动跳过 JSON Content-Type，让浏览器自己生成 boundary。
  return request<ResumeInfo>("/front/resume/upload", {
    method: "POST",
    body: formData
  });
}

/**
 * 查询当前登录用户的简历列表。
 */
export function listResumes() {
  return request<ResumeInfo[]>("/front/resume/list");
}

/**
 * 读取简历文件内容。
 * P表示参数描述，这里返回 Blob，页面可以把它转成临时地址后打开。
 */
export async function downloadResumeFile(resumeId: string) {
  const { tokenName, tokenValue } = getToken();
  const headers = new Headers();

  // 文件流接口也需要登录态，所以手动带上 Sa-Token 请求头。
  if (tokenValue) {
    headers.set(tokenName, tokenValue);
  }

  const response = await fetch(`/front/resume/${resumeId}/file`, {
    method: "GET",
    headers
  });

  const contentType = response.headers.get("Content-Type") || "";
  if (contentType.includes("application/json")) {
    const result = (await response.json()) as ApiResult<unknown>;
    throw new Error(result.message || "简历文件读取失败");
  }

  if (!response.ok) {
    throw new Error(`简历文件读取失败，HTTP状态码：${response.status}`);
  }

  return response.blob();
}
