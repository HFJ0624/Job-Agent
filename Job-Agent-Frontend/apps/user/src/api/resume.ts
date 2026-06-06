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
 * 修改简历名称。
 * P表示参数描述，resumeId 是路径参数，resumeName 是新的展示名称。
 */
export function updateResumeName(resumeId: string, resumeName: string) {
  return request<ResumeInfo>(`/front/resume/${resumeId}`, {
    method: "PUT",
    body: JSON.stringify({ resumeName })
  });
}

/**
 * 逻辑删除简历。
 * P表示参数描述，后端只会把 isDeleted 改为 1，不会直接删除 MinIO 文件。
 */
export function deleteResume(resumeId: string) {
  return request<null>(`/front/resume/${resumeId}`, {
    method: "DELETE"
  });
}

/**
 * 设置默认简历。
 * P表示参数描述，一个用户只能有一份默认简历，后端会自动取消其它简历默认状态。
 */
export function setDefaultResume(resumeId: string) {
  return request<ResumeInfo>(`/front/resume/${resumeId}/default`, {
    method: "PUT"
  });
}

/**
 * 解析简历文本。
 * P表示参数描述，后端会把解析出的文本保存到 rawText 字段，失败原因也会写入 rawText。
 */
export function parseResumeText(resumeId: string) {
  return request<ResumeInfo>(`/front/resume/${resumeId}/parse`, {
    method: "POST"
  });
}

/**
 * 读取简历文件内容。
 * P表示参数描述，这里返回 Blob，页面可以把它转成临时地址后打开。
 */
export async function downloadResumeFile(resumeId: string) {
  const file = await fetchResumeFile(resumeId);
  return file.blob;
}

/**
 * 读取简历文件内容和响应类型。
 * P表示参数描述，抽屉预览需要知道 Content-Type，下载只需要 Blob。
 */
export async function fetchResumeFile(resumeId: string) {
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

  return {
    blob: await response.blob(),
    contentType
  };
}
