package com.job.enums;

/**
 * 作者: hfj
 * 功能: 求职提醒类型枚举
 * 设计说明:
 * 1. INTERVIEW 表示面试提醒。
 * 2. FOLLOW_UP 表示跟进 HR 提醒。
 * 3. CUSTOM 表示用户自定义提醒。
 */
public enum ReminderType {

    /**
     * 面试提醒。
     */
    INTERVIEW("面试提醒"),

    /**
     * 跟进提醒。
     */
    FOLLOW_UP("跟进提醒"),

    /**
     * 自定义提醒。
     */
    CUSTOM("自定义提醒");

    private final String desc;

    ReminderType(String desc) {
        this.desc = desc;
    }

    public String getDesc() {
        return desc;
    }
}
