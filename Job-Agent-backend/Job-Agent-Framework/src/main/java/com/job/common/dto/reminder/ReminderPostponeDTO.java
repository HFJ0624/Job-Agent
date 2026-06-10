package com.job.common.dto.reminder;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

/**
 * 作者: hfj
 * 功能: 延期提醒 DTO
 */
@Data
public class ReminderPostponeDTO {

    /**
     * 新的提醒时间。
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date remindTime;

    /**
     * 新的事件时间，可选。
     *
     * 例如面试改期时，可以同时修改 eventTime。
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date eventTime;
}
