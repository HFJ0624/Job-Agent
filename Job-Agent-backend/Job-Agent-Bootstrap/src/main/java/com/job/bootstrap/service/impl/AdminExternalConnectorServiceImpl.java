package com.job.bootstrap.service.impl;

import com.job.bootstrap.agent.schema.AgentToolSchemaRegistry;
import com.job.bootstrap.service.AdminExternalConnectorService;
import com.job.common.agent.tool.AgentToolSchema;
import com.job.enums.AgentToolErrorCode;
import com.job.exception.AgentToolException;
import com.job.mcp.connector.tool.CalendarConnectorTool;
import com.job.mcp.connector.tool.EmailConnectorTool;
import com.job.mcp.connector.tool.JobSourceSyncConnectorTool;
import com.job.mcp.connector.tool.NotificationConnectorTool;
import com.job.mcp.connector.tool.RecruitmentPlatformConnectorTool;
import com.job.mcp.connector.tool.ResumeExportConnectorTool;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 后台外部连接器管理服务实现。
 * 方法内部保持显式 switch 分发，避免反射调用带来的参数不可控和安全边界模糊。
 */
@Service
@RequiredArgsConstructor
public class AdminExternalConnectorServiceImpl implements AdminExternalConnectorService {

    private static final String CONNECTOR_CATEGORY = "external_connector";

    private final AgentToolSchemaRegistry agentToolSchemaRegistry;
    private final RecruitmentPlatformConnectorTool recruitmentPlatformConnectorTool;
    private final EmailConnectorTool emailConnectorTool;
    private final CalendarConnectorTool calendarConnectorTool;
    private final NotificationConnectorTool notificationConnectorTool;
    private final ResumeExportConnectorTool resumeExportConnectorTool;
    private final JobSourceSyncConnectorTool jobSourceSyncConnectorTool;

    @Override
    public List<AgentToolSchema> listConnectorTools() {
        return agentToolSchemaRegistry.listAll()
                .stream()
                .filter(schema -> CONNECTOR_CATEGORY.equals(schema.getCategory()))
                .toList();
    }

    @Override
    public AgentToolSchema getConnectorTool(String toolName) {
        AgentToolSchema schema = agentToolSchemaRegistry.getRequired(toolName);
        if (!CONNECTOR_CATEGORY.equals(schema.getCategory())) {
            throw new AgentToolException(
                    AgentToolErrorCode.TOOL_PERMISSION_DENIED,
                    toolName,
                    "该工具不是外部连接器工具，不能在连接器管理页预览"
            );
        }
        return schema;
    }

    @Override
    public String preview(String toolName, Map<String, Object> params) {
        String canonicalToolName = agentToolSchemaRegistry.resolveToolName(toolName)
                .orElseThrow(() -> new AgentToolException(
                        AgentToolErrorCode.TOOL_NOT_REGISTERED,
                        toolName,
                        "外部连接器工具未注册: " + toolName
                ));
        getConnectorTool(canonicalToolName);

        /*
         * 1. 每个工具的参数都显式取值，避免前端传入多余字段影响执行。
         * 2. 第一版工具只返回 PREVIEW，不会真实连接第三方平台。
         * 3. 后续接真实适配器时，仍然应该保留这里的白名单分发。
         */
        return switch (canonicalToolName) {
            case "RecruitmentPlatformConnectorTool.searchExternalJobs" -> recruitmentPlatformConnectorTool.searchExternalJobs(
                    getString(params, "providerCode"),
                    getString(params, "keyword"),
                    getString(params, "city"),
                    getInteger(params, "limit")
            );
            case "EmailConnectorTool.readEmails" -> emailConnectorTool.readEmails(
                    getString(params, "providerCode"),
                    getString(params, "keyword"),
                    getInteger(params, "limit")
            );
            case "EmailConnectorTool.sendEmail" -> emailConnectorTool.sendEmail(
                    getString(params, "providerCode"),
                    getString(params, "to"),
                    getString(params, "subject"),
                    getString(params, "content")
            );
            case "CalendarConnectorTool.createInterviewEvent" -> calendarConnectorTool.createInterviewEvent(
                    getString(params, "providerCode"),
                    getString(params, "title"),
                    getString(params, "startTime"),
                    getString(params, "location")
            );
            case "NotificationConnectorTool.sendNotification" -> notificationConnectorTool.sendNotification(
                    getString(params, "channel"),
                    getString(params, "receiver"),
                    getString(params, "title"),
                    getString(params, "content")
            );
            case "ResumeExportConnectorTool.exportResume" -> resumeExportConnectorTool.exportResume(
                    getLong(params, "resumeId"),
                    getString(params, "format")
            );
            case "JobSourceSyncConnectorTool.syncJobs" -> jobSourceSyncConnectorTool.syncJobs(
                    getString(params, "providerCode"),
                    getString(params, "keyword"),
                    getString(params, "city"),
                    getInteger(params, "limit")
            );
            default -> throw new AgentToolException(
                    AgentToolErrorCode.TOOL_NOT_REGISTERED,
                    canonicalToolName,
                    "外部连接器预览暂不支持该工具: " + canonicalToolName
            );
        };
    }

    private String getString(Map<String, Object> params, String key) {
        Object value = params == null ? null : params.get(key);
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isBlank() ? null : text;
    }

    private Integer getInteger(Map<String, Object> params, String key) {
        Object value = params == null ? null : params.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        String text = String.valueOf(value).trim();
        return text.isBlank() ? null : Integer.parseInt(text);
    }

    private Long getLong(Map<String, Object> params, String key) {
        Object value = params == null ? null : params.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        String text = String.valueOf(value).trim();
        return text.isBlank() ? null : Long.parseLong(text);
    }
}
