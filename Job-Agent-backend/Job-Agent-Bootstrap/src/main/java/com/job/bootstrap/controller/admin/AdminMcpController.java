package com.job.bootstrap.controller.admin;

import com.job.bootstrap.mcp.McpClient;
import com.job.bootstrap.mcp.McpClientResults.McpExchange;
import com.job.bootstrap.mcp.McpClientResults.McpHandshake;
import com.job.bootstrap.mcp.McpClientResults.McpTools;
import com.job.common.entity.base.Result;
import com.job.common.entity.base.ResultCodeEnum;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 功能: 后台 MCP 协议调试接口（任务 G6）。
 *
 * 说明:
 * 1. 给连接器管理页提供“真实协议数据”展示：握手、工具列表、工具调用的原始 JSON-RPC 帧；
 * 2. 帧由 McpClient 在真实交换中保留（requestJson/responseJson），不是前端拼装的示意数据；
 * 3. 只暴露只读调试能力（initialize/tools/list/call），调用走的仍是同一 MCP 通道，
 *    连接器工具内部有自己的副作用确认边界。
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/mcp")
public class AdminMcpController {

    private final McpClient mcpClient;

    /**
     * MCP 握手：展示 initialize 的请求/响应帧与服务端信息。
     */
    @PostMapping("/initialize")
    public Result<Map<String, Object>> initialize() {
        McpExchange<McpHandshake> exchange = mcpClient.initialize();
        return Result.build(frames("initialize", exchange, exchange.result()), ResultCodeEnum.SUCCESS);
    }

    /**
     * 列出 MCP 服务端工具（tools/list 原始帧 + 工具定义）。
     */
    @GetMapping("/tools")
    public Result<Map<String, Object>> listTools() {
        McpExchange<McpTools> exchange = mcpClient.listTools();
        Map<String, Object> data = frames("tools/list", exchange, null);
        data.put("tools", exchange.result().tools());
        return Result.build(data, ResultCodeEnum.SUCCESS);
    }

    /**
     * 调用 MCP 工具（tools/call 原始帧 + 文本结果）。
     *
     * @param request {toolName, arguments}
     */
    @PostMapping("/call")
    public Result<Map<String, Object>> call(@RequestBody Map<String, Object> request) {
        String toolName = String.valueOf(request.get("toolName"));
        @SuppressWarnings("unchecked")
        Map<String, Object> arguments = request.get("arguments") instanceof Map<?, ?> map
                ? (Map<String, Object>) map
                : Map.of();

        McpExchange<String> exchange = mcpClient.callTool(toolName, arguments);
        Map<String, Object> data = frames("tools/call", exchange, null);
        data.put("toolName", toolName);
        data.put("text", exchange.result());
        return Result.build(data, ResultCodeEnum.SUCCESS);
    }

    // 组装“方法名 + 原始请求帧 + 原始响应帧”，前端直接展示
    private Map<String, Object> frames(String method, McpExchange<?> exchange, Object ignored) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("method", method);
        data.put("requestJson", exchange.requestJson());
        data.put("responseJson", exchange.responseJson());
        if (ignored != null) {
            data.put("result", ignored);
        }
        return data;
    }
}
