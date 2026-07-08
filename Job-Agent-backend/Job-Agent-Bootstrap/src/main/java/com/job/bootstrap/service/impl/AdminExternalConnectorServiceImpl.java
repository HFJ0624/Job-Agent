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
 *
 * <p>核心职责：为后台管理提供外部连接器工具的模式，包括工具列表查询、详情预览和参数化调用，所有工具通过显式 switch 分发，避免反射带来的安全风险。</p>
 *
 * <p>所属业务模块：后台管理模块（admin）/ Agent 工具模块（agent）</p>
 *
 * <p>主要调用链：
 * <ol>
 *   <li>管理员在后台连接器管理页调用 {@link #listConnectorTools} 查看所有外部连接器；</li>
 *   <li>点击预览时调用 {@link #getConnectorTool} 获取工具元数据；</li>
 *   <li>填写参数后调用 {@link #preview} 执行预览，验证参数和返回格式。</li>
 * </ol>
 * </p>
 *
 * <p>与其他核心组件的关系：
 * <ul>
 *   <li>依赖 {@link AgentToolSchemaRegistry} 管理工具元数据注册和查询；</li>
 *   <li>依赖各类 ConnectorTool 实现（招聘平台、邮件、日历、通知、简历导出、岗位同步）执行具体逻辑。</li>
 * </ul>
 * </p>
 *
 * <p>设计说明：
 * <ol>
 *   <li>方法内部保持显式 switch 分发，避免反射调用带来的参数不可控和安全边界模糊；</li>
 *   <li>预览模式只返回 PREVIEW 结果，不会真实连接第三方平台；</li>
 *   <li>每个工具参数都显式取值，前端传入多余字段不会影响执行；</li>
 *   <li>工具名通过注册表解析，支持别名映射和权限校验。</li>
 * </ol>
 * </p>
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

    /**
     * 查询所有已注册的外部连接器工具元数据。
     *
     * @return 外部连接器工具列表
     */
    @Override
    public List<AgentToolSchema> listConnectorTools() {
        return agentToolSchemaRegistry.listAll()
                .stream()
                .filter(schema -> CONNECTOR_CATEGORY.equals(schema.getCategory()))
                .toList();
    }

    /**
     * 根据工具名获取外部连接器元数据，并校验分类权限。
     *
     * @param toolName 工具名
     * @return 工具元数据
     * @throws AgentToolException 工具不存在或非外部连接器时抛出
     */
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

    /**
     * 预览外部连接器工具执行效果，参数显式取值，不连接真实第三方平台。
     *
     * @param toolName 工具名
     * @param params   调用参数
     * @return 预览结果
     * @throws AgentToolException 工具未注册或不支持预览时抛出
     */
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

    /**
     * 从参数 Map 中获取字符串值，空白内容返回 null。
     *
     * @param params 参数 Map
     * @param key    键
     * @return 非空字符串或 null
     */
    private String getString(Map<String, Object> params, String key) {
        Object value = params == null ? null : params.get(key);
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isBlank() ? null : text;
    }

    /**
     * 从参数 Map 中获取整数值，支持 Number 和字符串解析。
     *
     * @param params 参数 Map
     * @param key    键
     * @return 整数或 null
     */
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

    /**
     * 从参数 Map 中获取长整数值，支持 Number 和字符串解析。
     *
     * @param params 参数 Map
     * @param key    键
     * @return 长整数或 null
     */
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
