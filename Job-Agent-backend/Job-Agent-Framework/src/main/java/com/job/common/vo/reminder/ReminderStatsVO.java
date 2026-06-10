package com.job.common.vo.reminder;

import lombok.Data;

/**
 * 作者: hfj
 * 功能: 求职提醒统计 VO
 *
 * 前端求职进度页顶部卡片使用。
 */
@Data
public class ReminderStatsVO {

    /**
     * 待处理提醒总数。
     */
    private Long pendingCount;

    /**
     * 已到期提醒数。
     */
    private Long dueCount;

    /**
     * 今日提醒数。
     */
    private Long todayCount;

    /**
     * 面试提醒数。
     */
    private Long interviewCount;

    /**
     * 跟进提醒数。
     */
    private Long followUpCount;

    /**
     * 未读提醒数。
     */
    private Long unreadCount;
}
