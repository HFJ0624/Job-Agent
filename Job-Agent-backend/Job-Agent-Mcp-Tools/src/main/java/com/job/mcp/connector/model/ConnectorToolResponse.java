package com.job.mcp.connector.model;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

/**
 * 外部连接器统一响应。
 * 所有连接器第一版都返回这个结构，方便 Agent、Executor、Trace 和前端统一理解工具结果。
 */
@Data
@Builder
public class ConnectorToolResponse {

    /**
     * 工具唯一名称，统一使用“类名.方法名”，例如 EmailConnectorTool.readEmails。
     */
    private String toolName;

    /**
     * 连接器类型，例如 email、calendar、recruitment_platform。
     */
    private String connectorType;

    /**
     * 外部渠道或平台编码，例如 boss、qq-mail、google-calendar。
     */
    private String providerCode;

    /**
     * 连接器执行状态（任务 J3 起区分三态）:
     * - PREVIEW：已完成参数结构化与归属校验，未执行真实动作（未确认或后台预览）；
     * - EXECUTED：已真实执行（如文件已生成并上传），data 携带可核对结果；
     * - ERROR：参数校验/归属校验/真实执行失败，message 说明原因。
     */
    private String status;

    /**
     * 副作用类型，用于后续接入 Guardrails 判断是否需要拦截或确认。
     */
    private ConnectorSideEffectType sideEffectType;

    /**
     * 是否需要用户确认。发送邮件、发通知、创建日历、同步岗位这类动作默认需要确认。
     */
    private Boolean requiresUserConfirmation;

    /**
     * 是否还需要真实平台适配器。第一版为 true，避免误导调用方以为已经真正执行外部操作。
     */
    private Boolean requiresRealAdapter;

    /**
     * 给 Agent 或后台看的执行说明。
     */
    private String message;

    /**
     * 结构化输入摘要，后续真实适配器可以直接基于这些参数发起 API 请求。
     */
    private Map<String, Object> request;

    /**
     * 预览数据。第一版返回空结果或模拟摘要，不写入数据库、不调用外部服务。
     */
    private Map<String, Object> data;
}
