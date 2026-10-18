package com.job.mcp.protocol;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.job.mcp.connector.tool.CalendarConnectorTool;
import com.job.mcp.connector.tool.EmailConnectorTool;
import com.job.mcp.connector.tool.JobSourceSyncConnectorTool;
import com.job.mcp.connector.tool.NotificationConnectorTool;
import com.job.mcp.connector.tool.RecruitmentPlatformConnectorTool;
import com.job.mcp.connector.tool.ResumeExportConnectorTool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 功能: MCP 协议处理器（任务 G6，服务端核心）。
 *
 * 设计说明:
 * 1. 纯 JSON 逻辑、不碰 HTTP：输入一个 JSON-RPC 请求节点，输出响应节点（通知返回 null）。
 *    这样协议行为可以在无 Spring、无网络的环境下完整单测；
 * 2. 支持 MCP 最小可用子集：initialize（握手）/ ping / tools/list / tools/call；
 * 3. 错误按 JSON-RPC 2.0 规范：未知方法 -32601、参数非法（含未知工具）-32602、内部错误 -32603；
 * 4. tools/call 的工具分发保持显式 switch（与 AdminExternalConnectorServiceImpl 同风格），
 *    不反射、参数显式取值——协议出口的可控性与业务预览层一致；
 * 5. 工具业务异常不升级为协议错误：包装成 isError=true 的 call 结果返回，调用方自行决策。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class McpProtocolHandler {

    private final ObjectMapper objectMapper;
    private final RecruitmentPlatformConnectorTool recruitmentPlatformConnectorTool;
    private final EmailConnectorTool emailConnectorTool;
    private final CalendarConnectorTool calendarConnectorTool;
    private final NotificationConnectorTool notificationConnectorTool;
    private final ResumeExportConnectorTool resumeExportConnectorTool;
    private final JobSourceSyncConnectorTool jobSourceSyncConnectorTool;

    /**
     * 处理一个 JSON-RPC 请求。
     *
     * @param request 请求节点（已由控制器解析；解析失败由控制器侧直接构造错误响应）
     * @return 响应节点；通知类请求（无 id）返回 null，调用方不应输出响应体
     */
    public JsonNode handle(JsonNode request) {
        if (request == null || !request.isObject()) {
            return errorResponse(null, McpErrorCodes.INVALID_REQUEST, "不是合法的 JSON-RPC 请求对象");
        }

        JsonNode idNode = request.get("id");
        String method = request.path("method").asText(null);
        if (method == null || method.isBlank()) {
            return errorResponse(idNode, McpErrorCodes.INVALID_REQUEST, "缺少 method 字段");
        }

        JsonNode params = request.get("params");
        try {
            Object result = switch (method) {
                case "initialize" -> initializeResult();
                case "ping" -> Map.of();
                case "tools/list" -> toolsListResult();
                case "tools/call" -> toolsCallResult(params);
                default -> throw new McpProtocolException(McpErrorCodes.METHOD_NOT_FOUND, "未知方法: " + method);
            };
            if (isNotification(idNode)) {
                return null; // 通知不响应
            }
            return successResponse(idNode, result);
        } catch (McpProtocolException exception) {
            if (isNotification(idNode)) {
                return null;
            }
            return errorResponse(idNode, exception.getCode(), exception.getMessage());
        } catch (Exception exception) {
            log.warn("MCP 协议处理内部错误，method={}, error={}", method, exception.getMessage());
            if (isNotification(idNode)) {
                return null;
            }
            return errorResponse(idNode, McpErrorCodes.INTERNAL_ERROR, "服务端内部错误: " + exception.getMessage());
        }
    }

    // 握手：声明协议版本、能力与服务端信息
    private Map<String, Object> initializeResult() {
        Map<String, Object> capabilities = new LinkedHashMap<>();
        capabilities.put("tools", Map.of());

        Map<String, Object> serverInfo = new LinkedHashMap<>();
        serverInfo.put("name", "job-agent-mcp");
        serverInfo.put("version", "1.0.0");

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("protocolVersion", McpToolCatalog.PROTOCOL_VERSION);
        result.put("capabilities", capabilities);
        result.put("serverInfo", serverInfo);
        return result;
    }

    private Map<String, Object> toolsListResult() {
        List<Map<String, Object>> tools = new ArrayList<>();
        for (McpToolCatalog.McpToolDefinition definition : McpToolCatalog.listTools()) {
            Map<String, Object> tool = new LinkedHashMap<>();
            tool.put("name", definition.name());
            tool.put("description", definition.description());
            tool.put("inputSchema", definition.inputSchema());
            tools.add(tool);
        }
        return Map.of("tools", tools);
    }

    // tools/call：显式 switch 分发到连接器工具；结果包装成 MCP content 结构
    private Map<String, Object> toolsCallResult(JsonNode params) {
        if (params == null || !params.isObject() || !params.has("name")) {
            throw new McpProtocolException(McpErrorCodes.INVALID_PARAMS, "tools/call 缺少 name 参数");
        }
        String toolName = params.path("name").asText("");
        JsonNode argumentsNode = params.path("arguments");

        String text = switch (toolName) {
            case "RecruitmentPlatformConnectorTool.searchExternalJobs" -> recruitmentPlatformConnectorTool.searchExternalJobs(
                    text(argumentsNode, "providerCode"),
                    text(argumentsNode, "keyword"),
                    text(argumentsNode, "city"),
                    integer(argumentsNode, "limit")
            );
            case "EmailConnectorTool.readEmails" -> emailConnectorTool.readEmails(
                    text(argumentsNode, "providerCode"),
                    text(argumentsNode, "keyword"),
                    integer(argumentsNode, "limit")
            );
            case "EmailConnectorTool.sendEmail" -> emailConnectorTool.sendEmail(
                    text(argumentsNode, "providerCode"),
                    text(argumentsNode, "to"),
                    text(argumentsNode, "subject"),
                    text(argumentsNode, "content")
            );
            case "CalendarConnectorTool.createInterviewEvent" -> calendarConnectorTool.createInterviewEvent(
                    text(argumentsNode, "providerCode"),
                    text(argumentsNode, "title"),
                    text(argumentsNode, "startTime"),
                    text(argumentsNode, "location")
            );
            case "NotificationConnectorTool.sendNotification" -> notificationConnectorTool.sendNotification(
                    text(argumentsNode, "channel"),
                    text(argumentsNode, "receiver"),
                    text(argumentsNode, "title"),
                    text(argumentsNode, "content")
            );
            case "ResumeExportConnectorTool.exportResume" -> resumeExportConnectorTool.exportResume(
                    longValue(argumentsNode, "resumeId"),
                    text(argumentsNode, "format")
            );
            case "JobSourceSyncConnectorTool.syncJobs" -> jobSourceSyncConnectorTool.syncJobs(
                    text(argumentsNode, "providerCode"),
                    text(argumentsNode, "keyword"),
                    text(argumentsNode, "city"),
                    integer(argumentsNode, "limit")
            );
            default -> throw new McpProtocolException(McpErrorCodes.INVALID_PARAMS, "未知工具: " + toolName);
        };

        Map<String, Object> content = new LinkedHashMap<>();
        content.put("type", "text");
        content.put("text", text);
        return Map.of("content", List.of(content), "isError", false);
    }

    private boolean isNotification(JsonNode idNode) {
        return idNode == null || idNode.isNull();
    }

    private JsonNode successResponse(JsonNode idNode, Object result) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("jsonrpc", "2.0");
        response.put("id", idNode); // id 原样回显（字符串/数字均可）
        response.put("result", result);
        return objectMapper.valueToTree(response);
    }

    private JsonNode errorResponse(JsonNode idNode, int code, String message) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("code", code);
        error.put("message", message);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("jsonrpc", "2.0");
        response.put("id", idNode == null || idNode.isNull() ? null : idNode.asText());
        response.put("error", error);
        return objectMapper.valueToTree(response);
    }

    private String text(JsonNode node, String field) {
        if (node == null) {
            return null;
        }
        String value = node.path(field).asText(null);
        return value == null || value.isBlank() ? null : value.trim();
    }

    private Integer integer(JsonNode node, String field) {
        if (node == null || !node.path(field).isNumber()) {
            return null;
        }
        return node.path(field).asInt();
    }

    private Long longValue(JsonNode node, String field) {
        if (node == null || !node.path(field).isNumber()) {
            return null;
        }
        return node.path(field).asLong();
    }
}
