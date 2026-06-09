package com.job.common.entity.communication;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 作者: hfj
 * 功能: 求职沟通消息流水实体
 * 设计说明:
 * 1. job_communication_record 保存当前沟通记录的最新状态。
 * 2. job_communication_message 保存每一轮 HR 回复、AI 建议回复、用户发送内容。
 * 3. 这样前端可以展示完整沟通历史，而不是只展示最后一条消息。
 */
@Data
@TableName("job_communication_message")
public class JobCommunicationMessage {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 沟通记录ID。
     */
    private Long communicationId;

    /**
     * 当前用户ID。
     */
    private Long userId;

    /**
     * 消息类型：
     * HR_TO_USER / AI_SUGGESTION / USER_TO_HR。
     */
    private String senderType;

    /**
     * 消息内容。
     */
    private String messageContent;

    /**
     * AI 回复风格。
     */
    private String replyStyle;

    /**
     * 保存该消息后，沟通主记录的状态。
     */
    private String statusAfter;

    private Date createTime;

    private Date updateTime;

    private Integer isDeleted;
}
