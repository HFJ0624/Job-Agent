package com.job.common.dto.communication;

import lombok.Data;

/**
 * 作者: hfj
 * 功能: 创建求职沟通记录 DTO
 * 使用场景:
 * 1. 用户手动新增沟通记录。
 * 2. 生成打招呼语后，系统自动创建沟通记录。
 */
@Data
public class JobCommunicationCreateDTO {

    /**
     * 求职进度记录ID，可为空。
     */
    private Long applicationId;

    /**
     * 简历ID。
     */
    private Long resumeId;

    /**
     * 岗位ID，必填。
     */
    private Long jobId;

    /**
     * 打招呼语记录ID，可为空。
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
     * 打招呼语正文。
     */
    private String greetingText;

    /**
     * 备注。
     */
    private String note;
}
