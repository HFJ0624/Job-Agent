package com.job.common.dto.application;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Date;

/**
 * 作者:hfj
 * 功能:新增或更新求职记录请求参数
 */
@Data
public class JobApplicationSaveDTO {

    /**
     * 岗位ID。
     * 用户从岗位详情页加入进度时必须传。
     */
    @NotNull(message = "岗位ID不能为空")
    private Long jobId;

    /**
     * 简历ID。
     * 第一版可以为空，后续可以选择具体投递简历。
     */
    private Long resumeId;

    /**
     * 求职状态。
     * 不传时后端默认 INTERESTED。
     */
    private String status;

    /**
     * 优先级：LOW / NORMAL / HIGH。
     */
    private String priority;

    @Size(max = 64, message = "HR名称不能超过64位")
    private String hrName;

    @Size(max = 128, message = "HR联系方式不能超过128位")
    private String hrContact;

    /**
     * 投递时间。
     */
    private Date applyTime;

    /**
     * 面试时间。
     */
    private Date interviewTime;

    /**
     * 下次跟进时间。
     */
    private Date nextFollowTime;

    @Size(max = 1000, message = "备注不能超过1000位")
    private String note;
}
