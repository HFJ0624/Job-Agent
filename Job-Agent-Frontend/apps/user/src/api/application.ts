import { request } from "./request";
import type {
    JobApplicationInfo,
    JobApplicationStatsInfo,
    PageResult
} from "./types";

/**
 * 新增或更新求职记录。
 *
 * @param data 求职记录数据
 */
export function saveApplication(data: {
    jobId: number | string;
    resumeId?: number | string;
    status?: string;
    priority?: string;
    hrName?: string;
    hrContact?: string;
    applyTime?: string;
    interviewTime?: string;
    nextFollowTime?: string;
    note?: string;
}) {
    return request<JobApplicationInfo>("/front/applications", {
        method: "POST",
        body: JSON.stringify(data)
    });
}

/**
 * 分页查询求职记录。
 */
export function pageApplications(params: {
    pageNum: number;
    pageSize: number;
    status?: string;
    keyword?: string;
    city?: string;
    priority?: string;
}) {
    const query = new URLSearchParams();

    Object.entries(params).forEach(([key, value]) => {
        if (value !== undefined && value !== null && value !== "") {
            query.append(key, String(value));
        }
    });

    return request<PageResult<JobApplicationInfo>>(
        `/front/applications/page?${query.toString()}`
    );
}

/**
 * 修改求职状态。
 */
export function updateApplicationStatus(
    id: number | string,
    data: {
        status: string;
        note?: string;
        interviewTime?: string;
        nextFollowTime?: string;
    }
) {
    return request<JobApplicationInfo>(`/front/applications/${id}/status`, {
        method: "PUT",
        body: JSON.stringify(data)
    });
}

/**
 * 删除求职记录。
 */
export function deleteApplication(id: number | string) {
    return request<void>(`/front/applications/${id}`, {
        method: "DELETE"
    });
}

/**
 * 查询求职进度统计。
 */
export function getApplicationStats() {
    return request<JobApplicationStatsInfo>("/front/applications/stats");
}