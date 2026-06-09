package com.job.common.dto.communication;

import lombok.Data;

import java.util.Date;

/**
 * 作者: hfj
 * 功能: 标记面试邀约 DTO
 */
@Data
public class JobCommunicationInterviewDTO {

    /**
     * 面试时间。
     */
    private Date interviewTime;

    /**
     * 下次跟进时间。
     */
    private Date nextFollowTime;

    /**
     * 备注。
     */
    private String note;
}
