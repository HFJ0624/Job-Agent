package com.job.common.dto.reminder;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

/**
 * 作者: hfj
 * 功能: 创建自定义求职提醒 DTO
 */
@Data
public class ReminderCreateDTO {

    private Long applicationId;

    private Long communicationId;

    private Long resumeId;

    private Long jobId;

    /**
     * 提醒类型。
     *
     * 默认 CUSTOM。
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
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date eventTime;

    /**
     * 实际提醒时间。
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date remindTime;

    /**
     * 提前提醒分钟数。
     */
    private Integer advanceMinutes;
}
