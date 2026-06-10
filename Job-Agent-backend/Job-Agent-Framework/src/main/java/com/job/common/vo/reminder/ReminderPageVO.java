package com.job.common.vo.reminder;

import lombok.Data;

import java.util.List;

/**
 * 作者: hfj
 * 功能: 求职提醒分页 VO
 */
@Data
public class ReminderPageVO {

    private List<JobReminderVO> records;

    private Long total;

    private Long pageNo;

    private Long pageSize;
}
