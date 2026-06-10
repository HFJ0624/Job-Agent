package com.job.enums;

/**
 * 作者: hfj
 * 功能: 求职提醒状态枚举
 */
public enum ReminderStatus {

    /**
     * 待处理。
     */
    PENDING("待处理"),

    /**
     * 已完成。
     */
    DONE("已完成"),

    /**
     * 已取消。
     */
    CANCELLED("已取消");

    private final String desc;

    ReminderStatus(String desc) {
        this.desc = desc;
    }

    public String getDesc() {
        return desc;
    }
}
