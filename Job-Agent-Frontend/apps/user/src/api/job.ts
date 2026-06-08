import { request } from "./request";
import type {
  CommunicatePayload,
  FavoriteStateInfo,
  JobMessageInfo,
  PageResult,
  PositionDetailInfo,
  PositionInfo,
  JobMatchInfo,
  GreetingInfo
} from "./types";

/**
 * 查询前台岗位分页列表。
 * P表示参数描述，后端 /front/job/page 会强制只返回已发布岗位，草稿岗位不会出现在用户端。
 */
export function pageFrontPositions(params: {
  pageNo: number;
  pageSize: number;
  keyword?: string;
  city?: string;
  district?: string;
  jobCategory?: string;
  educationReq?: string;
  experienceReq?: string;
  workType?: string;
}) {
  const search = new URLSearchParams();
  search.set("pageNo", String(params.pageNo));
  search.set("pageSize", String(params.pageSize));

  if (params.keyword) search.set("keyword", params.keyword);
  if (params.city) search.set("city", params.city);
  if (params.district) search.set("district", params.district);
  if (params.jobCategory) search.set("jobCategory", params.jobCategory);
  if (params.educationReq) search.set("educationReq", params.educationReq);
  if (params.experienceReq) search.set("experienceReq", params.experienceReq);
  if (params.workType) search.set("workType", params.workType);

  return request<PageResult<PositionInfo>>(`/front/job/page?${search.toString()}`);
}

/**
 * 查询岗位详情。
 * P表示参数描述，详情接口会返回岗位完整信息、公司完整信息和当前用户收藏状态。
 */
export function getFrontPositionDetail(positionId: number | string) {
  return request<PositionDetailInfo>(`/front/job/${positionId}`);
}

/**
 * 收藏或取消收藏岗位。
 * P表示参数描述，后端会根据当前状态自动切换：未收藏变收藏，已收藏变取消。
 */
export function toggleJobFavorite(positionId: number | string) {
  return request<FavoriteStateInfo>(`/front/job/${positionId}/favorite`, {
    method: "POST"
  });
}

/**
 * 立即沟通。
 * P表示参数描述，content 为空时后端会生成默认消息并保存到沟通消息表。
 */
export function communicateWithHr(positionId: number | string, payload: CommunicatePayload = {}) {
  return request<JobMessageInfo>(`/front/job/${positionId}/communicate`, {
    method: "POST",
    body: JSON.stringify(payload)
  });
}

/**
 * 分析指定岗位和指定简历的匹配度。
 *
 * @param jobId 岗位ID
 * @param resumeId 简历ID
 */
export function matchJob(jobId: string, resumeId: string) {
  return request<JobMatchInfo>(`/front/job/${jobId}/match`, {
    method: "POST",
    body: JSON.stringify({
      resumeId
    })
  });
}

/**
 * 查询指定岗位和指定简历最近一次匹配记录。
 *
 * @param jobId 岗位ID
 * @param resumeId 简历ID
 */
export function getLatestJobMatch(jobId: string, resumeId: string) {
  return request<JobMatchInfo | null>(
    `/front/job/${jobId}/match-record?resumeId=${resumeId}`
  );
}

/**
 * 生成 HR 打招呼语。
 *
 * @param jobId 岗位ID
 * @param resumeId 简历ID
 * @param style 语气风格
 */
export function generateGreeting(jobId: string, resumeId: string, style: string) {
  return request<GreetingInfo>(`/front/job/${jobId}/greeting`, {
    method: "POST",
    body: JSON.stringify({
      resumeId,
      style
    })
  });
}
