package com.job.mcp.protocol;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 功能: MCP 工具目录（任务 G6）。
 *
 * 设计说明:
 * 1. 集中定义 6 个连接器工具的 MCP 元数据（名称、描述、入参 JSON Schema），
 *    tools/list 直接从这里产出，客户端与后台看到的工具定义完全一致；
 * 2. 与 AgentToolSchemaRegistry（Bootstrap 侧，管 Guardrails/Schema 校验）分工：
 *    本目录管“MCP 协议出口长什么样”，Registry 管“Agent 执行时怎么校验”；
 * 3. 协议层自包含（不依赖 Bootstrap），Mcp-Tools 模块可独立测试与演进。
 */
public final class McpToolCatalog {

    /** MCP 协议版本（initialize 握手声明）。 */
    public static final String PROTOCOL_VERSION = "2024-11-05";

    private McpToolCatalog() {
    }

    /**
     * 一条 MCP 工具定义。
     *
     * @param name        工具唯一名（与 Agent 工具名一致，便于两侧对齐）
     * @param description 工具描述（MCP tools/list 展示给模型/客户端）
     * @param inputSchema 入参 JSON Schema
     */
    public record McpToolDefinition(String name, String description, Map<String, Object> inputSchema) {
    }

    /** 全部连接器工具的 MCP 定义（顺序稳定，便于后台展示）。 */
    public static List<McpToolDefinition> listTools() {
        return List.of(
                new McpToolDefinition(
                        "RecruitmentPlatformConnectorTool.searchExternalJobs",
                        "从外部招聘平台搜索岗位（第一版返回调用预览，未真实请求平台 API）",
                        schema(Map.of(
                                "providerCode", stringSchema("招聘平台编码，例如 boss、liepin、lagou"),
                                "keyword", stringSchema("岗位关键词，例如 Java、产品经理"),
                                "city", stringSchema("城市，例如 上海、北京、杭州"),
                                "limit", integerSchema("最多返回数量")
                        ), List.of("providerCode", "keyword"))
                ),
                new McpToolDefinition(
                        "EmailConnectorTool.readEmails",
                        "读取邮箱中的求职相关邮件（第一版返回读取预览，未连接真实邮箱）",
                        schema(Map.of(
                                "providerCode", stringSchema("邮箱渠道编码，例如 qq-mail、gmail、outlook"),
                                "keyword", stringSchema("搜索关键词，例如 面试、offer、笔试"),
                                "limit", integerSchema("最多读取数量")
                        ), List.of("providerCode"))
                ),
                new McpToolDefinition(
                        "EmailConnectorTool.sendEmail",
                        "发送求职沟通邮件（副作用操作，真实执行前必须经过用户确认）",
                        schema(Map.of(
                                "providerCode", stringSchema("邮箱渠道编码"),
                                "to", stringSchema("收件人邮箱"),
                                "subject", stringSchema("邮件标题"),
                                "content", stringSchema("邮件正文")
                        ), List.of("providerCode", "to", "subject", "content"))
                ),
                new McpToolDefinition(
                        "CalendarConnectorTool.createInterviewEvent",
                        "创建面试日程（第一版返回调用预览，未写入真实日历）",
                        schema(Map.of(
                                "providerCode", stringSchema("日历渠道编码"),
                                "title", stringSchema("日程标题"),
                                "startTime", stringSchema("开始时间，ISO 格式"),
                                "location", stringSchema("地点或会议链接")
                        ), List.of("title", "startTime"))
                ),
                new McpToolDefinition(
                        "NotificationConnectorTool.sendNotification",
                        "发送系统通知（副作用操作，真实执行前必须经过用户确认）",
                        schema(Map.of(
                                "channel", stringSchema("通知渠道，例如站内、邮件"),
                                "receiver", stringSchema("接收人"),
                                "title", stringSchema("通知标题"),
                                "content", stringSchema("通知内容")
                        ), List.of("channel", "receiver", "content"))
                ),
                new McpToolDefinition(
                        "ResumeExportConnectorTool.exportResume",
                        "导出用户简历为 PDF、Word 或 Markdown（第一版返回导出预览）",
                        schema(Map.of(
                                "resumeId", integerSchema("简历ID"),
                                "format", stringSchema("导出格式，例如 PDF、DOCX、MARKDOWN")
                        ), List.of("resumeId", "format"))
                ),
                new McpToolDefinition(
                        "JobSourceSyncConnectorTool.syncJobs",
                        "从外部岗位源同步岗位到本地库（第一版返回调用预览）",
                        schema(Map.of(
                                "providerCode", stringSchema("岗位源编码"),
                                "keyword", stringSchema("岗位关键词"),
                                "city", stringSchema("城市"),
                                "limit", integerSchema("最多同步数量")
                        ), List.of("providerCode"))
                )
        );
    }

    // 组装 JSON Schema：properties + required
    private static Map<String, Object> schema(Map<String, Object> properties, List<String> required) {
        Map<String, Object> inputSchema = new LinkedHashMap<>();
        inputSchema.put("type", "object");
        inputSchema.put("properties", properties);
        inputSchema.put("required", required);
        return inputSchema;
    }

    private static Map<String, Object> stringSchema(String description) {
        Map<String, Object> property = new LinkedHashMap<>();
        property.put("type", "string");
        property.put("description", description);
        return property;
    }

    private static Map<String, Object> integerSchema(String description) {
        Map<String, Object> property = new LinkedHashMap<>();
        property.put("type", "integer");
        property.put("description", description);
        return property;
    }
}
