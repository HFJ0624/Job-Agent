import { request } from "./request";
import type {
    JobReminderInfo,
    ReminderPageResult,
    ReminderPostponePayload,
    ReminderStatsInfo
} from "./types";

/**
 * 分页查询提醒。
 */
export function pageReminders(params: {
    pageNo: number;
    pageSize: number;
    reminderType?: string;
    reminderStatus?: string;
    keyword?: string;
}) {
    const search = new URLSearchParams();

    search.set("pageNo", String(params.pageNo));
    search.set("pageSize", String(params.pageSize));

    if (params.reminderType) {
        search.set("reminderType", params.reminderType);
    }

    if (params.reminderStatus) {
        search.set("reminderStatus", params.reminderStatus);
    }

    if (params.keyword) {
        search.set("keyword", params.keyword);
    }

    return request<ReminderPageResult>(`/front/reminder/page?${search.toString()}`);
}

/**
 * 查询提醒统计。
 */
export function getReminderStats() {
    return request<ReminderStatsInfo>("/front/reminder/stats");
}

/**
 * 查询已到期提醒。
 */
export function listDueReminders() {
    return request<JobReminderInfo[]>("/front/reminder/due");
}

/**
 * 标记完成。
 */
export function markReminderDone(id: number | string) {
    return request<JobReminderInfo>(`/front/reminder/${id}/done`, {
        method: "POST"
    });
}

/**
 * 标记已读。
 */
export function markReminderRead(id: number | string) {
    return request<JobReminderInfo>(`/front/reminder/${id}/read`, {
        method: "POST"
    });
}

/**
 * 取消提醒。
 */
export function cancelReminder(id: number | string, reason?: string) {
    const search = new URLSearchParams();

    if (reason) {
        search.set("reason", reason);
    }

    return request<JobReminderInfo>(`/front/reminder/${id}/cancel?${search.toString()}`, {
        method: "POST"
    });
}

/**
 * 延期提醒。
 */
export function postponeReminder(
    id: number | string,
    payload: ReminderPostponePayload
) {
    return request<JobReminderInfo>(`/front/reminder/${id}/postpone`, {
        method: "POST",
        body: JSON.stringify(payload)
    });
}