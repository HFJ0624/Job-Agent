package com.job.common.dto.reminder;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

/**
 * 作者: hfj
 * 功能: 求职提醒分页查询 DTO
 */
@Data
public class ReminderQueryDTO {

    /**
     * 当前页。
     */
    private Long pageNo = 1L;

    /**
     * 每页数量。
     */
    private Long pageSize = 10L;

    /**
     * 提醒类型。
     *
     * INTERVIEW / FOLLOW_UP / CUSTOM。
     */
    private String reminderType;

    /**
     * 提醒状态。
     *
     * PENDING / DONE / CANCELLED。
     */
    private String reminderStatus;

    /**
     * 关键词。
     *
     * 可以搜索提醒标题、提醒内容、岗位名称、公司名称。
     */
    private String keyword;

    /**
     * 开始时间。
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date startTime;

    /**
     * 结束时间。
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date endTime;
}
