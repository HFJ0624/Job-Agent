package com.job.common.vo.communication;

import lombok.Data;

/**
 * 作者: hfj
 * 功能: 沟通记录统计 VO
 *
 * 前端顶部统计卡片使用。
 */
@Data
public class JobCommunicationStatsVO {

    /**
     * 总沟通记录数。
     */
    private Long totalCount;

    /**
     * 已生成话术数量。
     */
    private Long greetingGeneratedCount;

    /**
     * 已复制数量。
     */
    private Long copiedCount;

    /**
     * 已沟通数量。
     */
    private Long communicatedCount;

    /**
     * 已回复数量。
     */
    private Long repliedCount;

    /**
     * 已邀约面试数量。
     */
    private Long interviewInvitedCount;

    /**
     * 暂无回复数量。
     */
    private Long noReplyCount;

    /**
     * 已关闭数量。
     */
    private Long closedCount;
}
