package com.job.common.vo.reminder;

import lombok.Data;

import java.util.Date;

/**
 * 作者: hfj
 * 功能: 求职提醒展示 VO
 */
@Data
public class JobReminderVO {

    private Long id;

    private Long applicationId;

    private Long communicationId;

    private Long resumeId;

    private String resumeName;

    private Long jobId;

    private String jobTitle;

    private Long companyId;

    private String companyName;

    private String reminderType;

    private String reminderTypeDesc;

    private String reminderTitle;

    private String reminderContent;

    private Date eventTime;

    private Date remindTime;

    private Integer advanceMinutes;

    private String reminderStatus;

    private String reminderStatusDesc;

    private Integer isRead;

    /**
     * 是否已过期。
     *
     * PENDING 且 remindTime < 当前时间时为 true。
     */
    private Boolean overdue;

    /**
     * 距离提醒时间还有多少分钟。
     */
    private Long minutesLeft;

    private Date doneTime;

    private Date createTime;

    private Date updateTime;
}
