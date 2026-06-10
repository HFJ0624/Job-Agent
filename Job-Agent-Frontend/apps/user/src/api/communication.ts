import { request } from "./request";
import type {
    CommunicationCreatePayload,
    CommunicationInterviewPayload,
    CommunicationPageResult,
    CommunicationRecordInfo,
    CommunicationReplyPayload,
    CommunicationStatsInfo,
    CommunicationMessageInfo,
    CommunicationStatusUpdatePayload,
    HrReplyGeneratePayload,
    UserReplySentPayload,
    InterviewInviteConfirmPayload,
    InterviewInviteExtractInfo,
    InterviewInviteExtractPayload
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

/**
 * 保存 HR 回复并生成 AI 建议回复。
 */
export function generateHrReply(
    id: number | string,
    payload: HrReplyGeneratePayload
) {
    return request<CommunicationRecordInfo>(`/front/communication/${id}/hr-reply/generate`, {
        method: "POST",
        body: JSON.stringify(payload)
    });
}

/**
 * 标记用户已把回复发送给 HR。
 */
export function markUserReplySent(
    id: number | string,
    payload: UserReplySentPayload
) {
    return request<CommunicationRecordInfo>(`/front/communication/${id}/user-reply/sent`, {
        method: "POST",
        body: JSON.stringify(payload)
    });
}

/**
 * 手动更新沟通状态。
 */
export function updateCommunicationStatus(
    id: number | string,
    payload: CommunicationStatusUpdatePayload
) {
    return request<CommunicationRecordInfo>(`/front/communication/${id}/status`, {
        method: "POST",
        body: JSON.stringify(payload)
    });
}

/**
 * 查询沟通消息流水。
 */
export function listCommunicationMessages(id: number | string) {
    return request<CommunicationMessageInfo[]>(`/front/communication/${id}/messages`);
}
/**
 * 从 HR 回复中提取面试邀约信息。
 */
export function extractInterviewInvite(
    id: number | string,
    payload: InterviewInviteExtractPayload
) {
    return request<InterviewInviteExtractInfo>(`/front/communication/${id}/interview/extract`, {
        method: "POST",
        body: JSON.stringify(payload)
    });
}

/**
 * 用户确认并保存面试邀约信息。
 */
export function confirmInterviewInvite(
    id: number | string,
    payload: InterviewInviteConfirmPayload
) {
    return request<CommunicationRecordInfo>(`/front/communication/${id}/interview/confirm`, {
        method: "POST",
        body: JSON.stringify(payload)
    });
}