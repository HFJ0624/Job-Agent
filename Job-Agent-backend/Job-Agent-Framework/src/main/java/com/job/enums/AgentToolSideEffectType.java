package com.job.enums;

/**
 * 作者:hfj
 * 功能:Agent 工具副作用类型
 * 日期:2026/6/20
 */
public enum AgentToolSideEffectType {

    /**
     * 只读工具，不写业务数据。
     */
    READ_ONLY,

    /**
     * 会生成业务记录。
     * 例如简历评分记录、岗位匹配记录、面试准备记录。
     */
    WRITE_BUSINESS_RECORD,

    /**
     * 会改变用户状态或偏好。
     * 第一版暂未开放给 Agent 使用，预留给后续偏好更新、状态修改等能力。
     */
    UPDATE_USER_STATE,

    /**
     * 会触达外部系统或对外发送消息。
     * 这类工具风险最高，后续必须强制用户确认。
     */
    EXTERNAL_ACTION
}
