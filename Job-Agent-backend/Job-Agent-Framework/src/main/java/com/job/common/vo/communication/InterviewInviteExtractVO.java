package com.job.common.vo.communication;

import lombok.Data;

/**
 * 作者: hfj
 * 功能: HR 面试邀约信息提取结果 VO
 *
 * 使用场景:
 * 1. 用户复制 HR 回复到系统。
 * 2. 系统判断 HR 是否在邀约面试。
 * 3. 如果是，则提取时间、方式、地点、会议链接等信息。
 *
 * 注意:
 * 这个 VO 是 AI 提取后的结构化结果。
 */
@Data
public class InterviewInviteExtractVO {

    /**
     * 是否识别为面试邀约。
     *
     * true：HR 回复中包含面试邀约或面试时间协商。
     * false：普通回复，不是面试邀约。
     */
    private Boolean interviewInvited;

    /**
     * 面试时间。
     *
     * 格式要求：yyyy-MM-dd HH:mm:ss。
     * 如果 HR 只说“明天下午”但没有具体时间，可以为空。
     */
    private String interviewTime;

    /**
     * 面试日期原文。
     *
     * 例如：明天、周三、6月12日。
     */
    private String dateText;

    /**
     * 面试时间原文。
     *
     * 例如：下午三点、14:30、上午10点。
     */
    private String timeText;

    /**
     * 面试方式。
     *
     * ONLINE / OFFLINE / PHONE / UNKNOWN。
     */
    private String interviewMethod;

    /**
     * 面试方式中文描述。
     */
    private String interviewMethodDesc;

    /**
     * 面试地点。
     *
     * 线下面试：公司地址。
     * 线上面试：腾讯会议、飞书会议等。
     */
    private String interviewLocation;

    /**
     * 线上平台。
     *
     * 例如：腾讯会议、飞书、Zoom、电话。
     */
    private String interviewPlatform;

    /**
     * 会议链接。
     */
    private String meetingLink;

    /**
     * 面试联系人。
     */
    private String interviewContact;

    /**
     * 是否需要用户进一步确认。
     *
     * 例如 HR 说“明天下午方便吗？”
     * 这种不是最终确定时间，需要用户确认。
     */
    private Boolean needUserConfirm;

    /**
     * 需要向 HR 确认的问题。
     *
     * 例如：请确认面试形式、会议链接、具体时间。
     */
    private String confirmQuestion;

    /**
     * AI 提取置信度。
     *
     * 0-100。
     */
    private Double confidence;

    /**
     * 提取说明。
     *
     * 例如：识别到“明天下午3点”和“线上面试”。
     */
    private String reason;
}
