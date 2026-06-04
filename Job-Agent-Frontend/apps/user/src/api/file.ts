import { request } from "./request";
import type { FileUploadResponse } from "./types";

export function uploadAvatar(file: File) {
  const formData = new FormData();
  formData.append("file", file);

  return request<FileUploadResponse>("/front/file/avatar", {
    method: "POST",
    body: formData
  });
}
