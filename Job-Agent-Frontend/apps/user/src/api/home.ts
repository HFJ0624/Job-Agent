import { request } from "./request";
import type { HomeOverviewInfo } from "./types";

/**
 * 查询用户端首页聚合数据。
 * 说明: 后端会统一返回推荐岗位、热门公司、简历报告和 AI 建议，前端首页不再维护本地假数据。
 */
export function getHomeOverview() {
  return request<HomeOverviewInfo>("/front/home/overview");
}
