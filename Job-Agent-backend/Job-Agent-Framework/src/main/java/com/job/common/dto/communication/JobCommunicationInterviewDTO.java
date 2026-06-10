package com.job.common.dto.communication;

import com.fasterxml.jackson.annotation.JsonFormat;
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
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date interviewTime;

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
