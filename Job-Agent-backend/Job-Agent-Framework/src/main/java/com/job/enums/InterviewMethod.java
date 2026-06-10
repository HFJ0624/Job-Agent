package com.job.enums;

/**
 * 作者: hfj
 * 功能: 面试方式枚举
 * 设计说明:
 * 1. HR 回复中可能出现线上、线下、电话等不同面试方式。
 * 2. 后端统一用枚举编码保存，前端展示中文描述。
 */
public enum InterviewMethod {

    /**
     * 线上面试。
     * 例如：腾讯会议、飞书会议、Zoom、Teams。
     */
    ONLINE("线上面试"),

    /**
     * 线下面试。
     * 例如：到公司现场面试。
     */
    OFFLINE("线下面试"),

    /**
     * 电话面试。
     */
    PHONE("电话面试"),

    /**
     * 未知。
     * HR 没说清楚，或者 AI 无法判断。
     */
    UNKNOWN("未知");

    private final String desc;

    InterviewMethod(String desc) {
        this.desc = desc;
    }

    public String getDesc() {
        return desc;
    }
}
