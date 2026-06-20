package com.job.enums;

/**
 * 作者:hfj
 * 功能:Agent 工具权限类型
 * 日期:2026/6/20
 */
public enum AgentToolPermissionType {

    /**
     * 需要前台登录用户。
     * 当前 Agent 工具都从 Sa-Token 登录态注入 userId，不能由模型传入 userId。
     */
    LOGIN_USER,

    /**
     * 需要后台管理员权限。
     * 第一版暂未给 Agent 开放后台写操作，先预留给后续管理类工具。
     */
    ADMIN_USER,

    /**
     * 仅系统内部可调用。
     * 适合后续定时任务、离线索引、批处理等工具。
     */
    SYSTEM
}
