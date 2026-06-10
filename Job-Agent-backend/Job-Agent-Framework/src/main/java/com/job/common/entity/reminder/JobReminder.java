package com.job.common.entity.reminder;

import com.baomidou.mybatisplus.annotation.TableName;
import com.job.common.entity.base.BaseEntity;
import lombok.Data;

import java.util.Date;

/**
 * 作者: hfj
 * 功能: 求职提醒实体
 * 表设计说明:
 * 1. 面试提醒、HR跟进提醒、自定义提醒都保存到这张表。
 * 2. communicationId 用于关联沟通记录。
 * 3. applicationId 用于关联求职进度。
 * 4. remindTime 是系统应该提醒用户的时间。
 * 5. eventTime 是事件实际发生时间，例如面试时间。
 */
@Data
@TableName("job_reminder")
public class JobReminder extends BaseEntity {

    /**
     * 当前用户ID。
     */
    private Long userId;

    /**
     * 求职进度记录ID。
     */
    private Long applicationId;

    /**
     * 沟通记录ID。
     */
    private Long communicationId;

    /**
     * 简历ID。
     */
    private Long resumeId;

    /**
     * 岗位ID。
     */
    private Long jobId;

    /**
     * 提醒类型。
     *
     * INTERVIEW / FOLLOW_UP / CUSTOM。
     */
    private String reminderType;

    /**
     * 提醒标题。
     */
    private String reminderTitle;

    /**
     * 提醒内容。
     */
    private String reminderContent;

    /**
     * 事件发生时间。
     *
     * 例如面试时间是 15:00，那么 eventTime = 15:00。
     */
    private Date eventTime;

    /**
     * 实际提醒时间。
     *
     * 例如面试前 30 分钟提醒，那么 remindTime = 14:30。
     */
    private Date remindTime;

    /**
     * 提前提醒分钟数。
     */
    private Integer advanceMinutes;

    /**
     * 提醒状态。
     *
     * PENDING / DONE / CANCELLED。
     */
    private String reminderStatus;

    /**
     * 是否已读。
     */
    private Integer isRead;

    /**
     * 完成时间。
     */
    private Date doneTime;

    /**
     * 取消原因。
     */
    private String cancelReason;
}
