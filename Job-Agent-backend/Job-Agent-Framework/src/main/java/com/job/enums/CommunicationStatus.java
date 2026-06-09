package com.job.enums;

/**
 * 作者: hfj
 * 功能: 求职沟通状态枚举
 * 设计说明:
 * 1. 这个状态描述的是“用户和 HR 沟通进度”。
 * 2. 它不是系统自动投递状态。
 * 3. 用户在 Boss 直聘等外部平台沟通后，把进度回填到系统中。
 */
public enum CommunicationStatus {

    /**
     * 已生成打招呼语。
     * 说明:
     * 用户还没有复制，也没有真正去外部平台沟通。
     */
    GREETING_GENERATED("已生成话术"),

    /**
     * 已复制话术。
     * 说明:
     * 用户点击了复制按钮，准备去 Boss 直聘等平台发送。
     */
    COPIED("已复制"),

    /**
     * 已沟通。
     * 说明:
     * 用户已经在外部平台向 HR 发送了消息。
     */
    COMMUNICATED("已沟通"),

    /**
     * 已收到回复。
     * 说明:
     * 用户把 HR 回复内容录入到了系统里。
     */
    REPLIED("已回复"),

    /**
     * 已邀约面试。
     * 说明:
     * HR 回复中包含面试邀约，用户填写了面试时间。
     */
    INTERVIEW_INVITED("邀约面试"),

    /**
     * 暂无回复。
     * 说明:
     * 用户标记该沟通暂时没有回复。
     */
    NO_REPLY("暂无回复"),

    /**
     * AI 已经根据 HR 回复生成建议回复。
     * 说明:
     * 用户还没有确认是否复制发送给 HR。
     */
    AI_REPLY_GENERATED("已生成回复"),

    /**
     * 用户已把 AI 回复或自定义回复发送给 HR。
     */
    USER_REPLIED("已回复HR"),

    /**
     * 已关闭。
     * 说明:
     * 用户不再跟进这个岗位。
     */
    CLOSED("已关闭");

    private final String desc;

    CommunicationStatus(String desc) {
        this.desc = desc;
    }

    public String getDesc() {
        return desc;
    }
}
