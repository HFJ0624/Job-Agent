package com.job.common.dto.application;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Date;

/**
 * 作者:hfj
 * 功能:修改求职记录状态请求参数
 */
@Data
public class JobApplicationStatusUpdateDTO {

    /**
     * 新状态。
     */
    @NotBlank(message = "状态不能为空")
    private String status;

    /**
     * 状态变更备注。
     */
    @Size(max = 1000, message = "备注不能超过1000位")
    private String note;

    /**
     * 面试时间。
     * 状态变为 INTERVIEWING 时可以填写。
     */
    private Date interviewTime;

    /**
     * 下次跟进时间。
     */
    private Date nextFollowTime;
}
