import { request } from "./request";
import type {
    JobRecommendInfo,
    UserJobPreferenceInfo
} from "./types";

/**
 * 查询当前用户求职偏好。
 */
export function getJobPreference() {
    return request<UserJobPreferenceInfo | null>("/front/job-preference");
}

/**
 * 保存当前用户求职偏好。
 *
 * @param data 求职偏好表单
 */
export function saveJobPreference(data: UserJobPreferenceInfo) {
    return request<UserJobPreferenceInfo>("/front/job-preference", {
        method: "POST",
        body: JSON.stringify(data)
    });
}

/**
 * 根据求职偏好推荐岗位。
 *
 * @param params 查询参数
 */
export function recommendJobs(params: {
    keyword?: string;
    city?: string;
    limit?: number;
}) {
    const query = new URLSearchParams();

    Object.entries(params).forEach(([key, value]) => {
        if (value !== undefined && value !== null && value !== "") {
            query.append(key, String(value));
        }
    });

    return request<JobRecommendInfo[]>(
        `/front/job-preference/recommend?${query.toString()}`
    );
}