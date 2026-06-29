import { request } from "./request";
import type { FrontFollowUpCenterInfo } from "./types";

/**
 * 查询用户端求职跟进 Agent 中心。
 */
export function getFollowUpCenter() {
  return request<FrontFollowUpCenterInfo>("/front/follow-up-agent/center");
}
