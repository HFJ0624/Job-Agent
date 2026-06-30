package com.job.common.dto.communication;

import lombok.Data;

import java.util.Date;

/**
 * HR 回复识别确认执行请求。
 *
 * 说明：
 * 用户可以只确认部分动作，例如只保存 HR 回复，不更新求职状态。
 */
@Data
public class HrReplyRecognitionConfirmDTO {

    /**
     * 是否把 HR 回复写入沟通记录。
     */
    private Boolean saveCommunication;

    /**
     * 是否更新求职记录状态。
     */
    private Boolean updateApplicationStatus;

    /**
     * 是否同步创建提醒。
     */
    private Boolean createReminder;

    /**
     * 是否触发面试准备任务。
     * 第一版由已有求职状态变更链路触发，这里先记录用户意图。
     */
    private Boolean generateInterviewPrepare;

    /**
     * 用户确认后的求职状态，允许覆盖 AI 建议。
     */
    private String suggestedStatus;

    /**
     * 用户确认后的面试时间。
     */
    private Date interviewTime;

    /**
     * 用户确认后的下次跟进时间。
     */
    private Date nextFollowTime;

    /**
     * 用户备注。
     */
    private String note;
}
