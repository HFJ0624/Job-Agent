package com.job.enums;

/**
 * 作者: hfj
 * 功能: 沟通消息类型枚举
 *
 * 设计说明:
 * 一条沟通记录下面可能有多轮消息。
 * 例如:
 * 1. HR 回复用户。
 * 2. AI 生成建议回复。
 * 3. 用户复制回复给 HR。
 */
public enum CommunicationMessageType {

    /**
     * HR 发给用户的消息。
     */
    HR_TO_USER,

    /**
     * AI 生成的建议回复。
     */
    AI_SUGGESTION,

    /**
     * 用户最终发送给 HR 的消息。
     */
    USER_TO_HR
}