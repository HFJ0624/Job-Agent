package com.job.common.vo.communication;

import lombok.Data;

import java.util.Date;

/**
 * 作者: hfj
 * 功能: 沟通消息流水 VO
 *
 * 前端用途:
 * 1. 展示 HR 回复。
 * 2. 展示 AI 建议回复。
 * 3. 展示用户已发送给 HR 的回复。
 */
@Data
public class JobCommunicationMessageVO {

    private Long id;

    private Long communicationId;

    private String senderType;

    private String senderTypeDesc;

    private String messageContent;

    private String replyStyle;

    private String statusAfter;

    private Date createTime;
}
