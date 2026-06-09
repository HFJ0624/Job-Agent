package com.job.common.vo.communication;

import lombok.Data;

import java.util.Date;

/**
 * 作者: hfj
 * 功能: 求职沟通记录展示 VO
 * 说明:
 * 前端展示沟通记录时不要直接返回 Entity。
 * VO 可以额外带岗位名称、公司名称等展示字段。
 */
@Data
public class JobCommunicationRecordVO {

    /**
     * 沟通记录ID。
     */
    private Long id;

    /**
     * 求职进度记录ID。
     */
    private Long applicationId;

    /**
     * 简历ID，前端可以内部使用，但页面上不要直接展示。
     */
    private Long resumeId;

    /**
     * 简历名称。
     * 例如：Java后端简历、AI应用开发简历。
     */
    private String resumeName;

    /**
     * 岗位ID，前端可以内部使用，但页面上不要直接展示。
     */
    private Long jobId;

    /**
     * 岗位名称。
     * 例如：Java开发工程师、后端开发工程师。
     */
    private String jobTitle;

    /**
     * 公司ID。
     */
    private Long companyId;

    /**
     * 公司名称。
     */
    private String companyName;

    /**
     * 工作城市。
     */
    private String jobCity;

    /**
     * 最低薪资。
     */
    private Integer minSalary;

    /**
     * 最高薪资。
     */
    private Integer maxSalary;

    /**
     * 薪资展示文本。
     * 例如：15-30K。
     */
    private String salaryText;

    /**
     * 打招呼语记录ID。
     */
    private Long greetingRecordId;

    /**
     * 沟通平台。
     */
    private String platform;

    /**
     * 外部岗位链接。
     */
    private String externalJobUrl;

    /**
     * HR 名称。
     */
    private String hrName;

    /**
     * HR 联系方式。
     */
    private String hrContact;

    /**
     * 发送给 HR 的打招呼语。
     */
    private String greetingText;

    /**
     * HR 回复内容。
     */
    private String hrReply;

    /**
     * 沟通状态。
     */
    private String communicationStatus;

    /**
     * 沟通状态中文。
     */
    private String communicationStatusDesc;

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

    /**
     * AI 生成给 HR 的最新回复建议。
     */
    private String aiReplyText;

    /**
     * 用户最终发送给 HR 的回复内容。
     */
    private String userReplyText;

    private Date createTime;

    private Date updateTime;
}
