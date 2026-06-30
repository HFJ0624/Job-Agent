package com.job.common.entity.communication;

import com.baomidou.mybatisplus.annotation.TableName;
import com.job.common.entity.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.util.Date;

/**
 * HR 回复识别记录。
 *
 * 说明：
 * 1. 这张表只记录“AI 如何理解 HR 回复”，不是直接替用户执行动作。
 * 2. 用户确认前 confirmStatus 为 PENDING，所有业务更新都不会真正落地。
 * 3. 用户确认后再写入沟通记录、求职进度、提醒等业务表，形成可追溯闭环。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("hr_reply_recognition_record")
public class HrReplyRecognitionRecord extends BaseEntity {

    /**
     * 当前登录用户 ID，用于权限隔离。
     */
    private Long userId;

    /**
     * 求职记录 ID。跟进中心入口通常只有 applicationId。
     */
    private Long applicationId;

    /**
     * 沟通记录 ID。沟通记录入口会传入该字段，跟进中心入口允许为空。
     */
    private Long communicationId;

    /**
     * 岗位 ID 快照，便于后续排查识别上下文。
     */
    private Long jobId;

    /**
     * 简历 ID 快照。
     */
    private Long resumeId;

    /**
     * 公司名称快照。
     */
    private String companyName;

    /**
     * 岗位名称快照。
     */
    private String jobTitle;

    /**
     * 识别前的求职状态或沟通状态。
     */
    private String currentStatus;

    /**
     * HR 回复原文。
     */
    private String hrReplyText;

    /**
     * AI 识别出的意图类型。
     * 例如：INTERVIEW_INVITE / NEED_MORE_INFO / WAITING / REJECTED / OFFER / GENERAL_REPLY。
     */
    private String intentType;

    /**
     * 置信度，范围 0-1。
     */
    private BigDecimal confidence;

    /**
     * 建议求职状态。
     */
    private String suggestedStatus;

    /**
     * 识别出的面试时间。
     */
    private Date interviewTime;

    /**
     * 建议下次跟进时间。
     */
    private Date nextFollowTime;

    /**
     * 待办事项 JSON 数组。
     */
    private String todoItemsJson;

    /**
     * 建议回复 HR 的话术。
     */
    private String replySuggestion;

    /**
     * AI 给出的识别理由。
     */
    private String reason;

    /**
     * 模型原始 JSON，方便排查 Prompt 或解析问题。
     */
    private String recognitionJson;

    /**
     * PENDING / CONFIRMED / CANCELLED。
     */
    private String confirmStatus;

    /**
     * 用户确认后实际执行了哪些动作。
     */
    private String executedActionsJson;

    /**
     * 确认执行失败时记录原因。
     */
    private String errorMsg;
}
