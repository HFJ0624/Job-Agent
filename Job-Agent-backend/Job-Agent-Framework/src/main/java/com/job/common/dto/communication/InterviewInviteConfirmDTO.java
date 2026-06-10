package com.job.common.dto.communication;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

/**
 * 作者: hfj
 * 功能: 用户确认面试邀约信息 DTO
 *
 * 设计说明:
 * AI 提取的信息可能不完全准确，所以最终由用户确认保存。
 */
@Data
public class InterviewInviteConfirmDTO {

    /**
     * 面试时间。
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date interviewTime;

    /**
     * 面试方式。
     *
     * ONLINE / OFFLINE / PHONE / UNKNOWN。
     */
    private String interviewMethod;

    /**
     * 面试地点。
     */
    private String interviewLocation;

    /**
     * 线上面试平台。
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
     * 下次跟进时间。
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date nextFollowTime;

    /**
     * 备注。
     */
    private String note;
}
