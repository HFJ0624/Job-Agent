package com.job.common.entity.communication;

import com.baomidou.mybatisplus.annotation.TableName;
import com.job.common.entity.base.BaseEntity;
import lombok.Data;

import java.util.Date;

/**
 * 作者: hfj
 * 功能: 求职沟通记录实体
 *
 * 表说明:
 * 这张表记录用户和 HR 的沟通过程。
 * 典型流程:
 * 1. 系统生成 HR 打招呼语。
 * 2. 自动创建沟通记录，状态为 GREETING_GENERATED。
 * 3. 用户复制话术，状态变为 COPIED。
 * 4. 用户去 Boss 直聘沟通后，状态变为 COMMUNICATED。
 * 5. 用户录入 HR 回复后，状态变为 REPLIED。
 * 6. 如果 HR 邀约面试，状态变为 INTERVIEW_INVITED。
 */
@Data
@TableName("job_communication_record")
public class JobCommunicationRecord extends BaseEntity {

    /**
     * 当前用户ID。
     * 必须来自登录态，不允许前端随便传。
     */
    private Long userId;

    /**
     * 求职进度记录ID。
     * 第一版可以为空，后续可以关联 job_application_record。
     */
    private Long applicationId;

    /**
     * 简历ID。
     */
    private Long resumeId;

    /**
     * 岗位ID。
     */
    private Long jobId;

    /**
     * 打招呼语记录ID。
     */
    private Long greetingRecordId;

    /**
     * 沟通平台。
     * 例如 BOSS、LAGOU、LIEPIN、EMAIL、OTHER。
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
     * HR 联系方式或平台标识。
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
}
