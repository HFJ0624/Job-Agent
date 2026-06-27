package com.job.mcp.connector.model;

/**
 * 外部连接器副作用类型。
 * 第一版只用于工具返回和后续 Guardrails 接入时判断风险等级。
 */
public enum ConnectorSideEffectType {

    /**
     * 只读操作，例如读取邮件、查询外部岗位。
     */
    READ,

    /**
     * 写入或发送操作，例如发送邮件、创建日历、同步岗位。
     */
    WRITE,

    /**
     * 导出文件操作，例如导出 PDF/Word 简历。
     */
    EXPORT
}
