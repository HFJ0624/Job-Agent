import { request } from "./request";
import type {
    CommunicationCreatePayload,
    CommunicationInterviewPayload,
    CommunicationPageResult,
    CommunicationRecordInfo,
    CommunicationReplyPayload,
    CommunicationStatsInfo
} from "./types";

/**
 * 分页查询沟通记录。
 *
 * @param params 查询参数
 */
export function pageCommunications(params: {
    pageNo: number;
    pageSize: number;
    status?: string;
    platform?: string;
    keyword?: string;
}) {
    const search = new URLSearchParams();

    search.set("pageNo", String(params.pageNo));
    search.set("pageSize", String(params.pageSize));

    if (params.status) {
        search.set("status", params.status);
    }

    if (params.platform) {
        search.set("platform", params.platform);
    }

    if (params.keyword) {
        search.set("keyword", params.keyword);
    }

    return request<CommunicationPageResult>(`/front/communication/page?${search.toString()}`);
}

/**
 * 查询沟通统计数据。
 */
export function getCommunicationStats() {
    return request<CommunicationStatsInfo>("/front/communication/stats");
}

/**
 * 查询沟通详情。
 */
export function getCommunicationDetail(id: number | string) {
    return request<CommunicationRecordInfo>(`/front/communication/${id}`);
}

/**
 * 手动新增沟通记录。
 */
export function createCommunication(payload: CommunicationCreatePayload) {
    return request<CommunicationRecordInfo>("/front/communication", {
        method: "POST",
        body: JSON.stringify(payload)
    });
}

/**
 * 标记已复制话术。
 */
export function markCommunicationCopied(id: number | string) {
    return request<CommunicationRecordInfo>(`/front/communication/${id}/copied`, {
        method: "POST"
    });
}

/**
 * 标记已沟通。
 */
export function markCommunicationCommunicated(id: number | string) {
    return request<CommunicationRecordInfo>(`/front/communication/${id}/communicated`, {
        method: "POST"
    });
}

/**
 * 保存 HR 回复。
 */
export function saveCommunicationReply(
    id: number | string,
    payload: CommunicationReplyPayload
) {
    return request<CommunicationRecordInfo>(`/front/communication/${id}/reply`, {
        method: "POST",
        body: JSON.stringify(payload)
    });
}

/**
 * 标记面试邀约。
 */
export function markCommunicationInterview(
    id: number | string,
    payload: CommunicationInterviewPayload
) {
    return request<CommunicationRecordInfo>(`/front/communication/${id}/interview`, {
        method: "POST",
        body: JSON.stringify(payload)
    });
}

/**
 * 关闭沟通记录。
 */
export function closeCommunication(id: number | string) {
    return request<CommunicationRecordInfo>(`/front/communication/${id}/close`, {
        method: "POST"
    });
}