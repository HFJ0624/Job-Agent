import { request } from "./request";
import type { UpdateProfilePayload, UserInfo } from "./types";

export function updateProfile(payload: UpdateProfilePayload) {
  return request<UserInfo>("/front/user/profile", {
    method: "PUT",
    body: JSON.stringify(payload)
  });
}
