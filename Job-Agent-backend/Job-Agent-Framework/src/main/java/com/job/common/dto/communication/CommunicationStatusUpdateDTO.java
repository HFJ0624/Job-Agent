package com.job.common.dto.communication;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

/**
 * 作者: hfj
 * 功能: 沟通状态流转 DTO
 */
@Data
public class CommunicationStatusUpdateDTO {

    /**
     * 新状态。
     */
    private String communicationStatus;

    /**
     * 面试时间。
     * 如果状态是 INTERVIEW_INVITED，可以填写。
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
