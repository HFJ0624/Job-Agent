package com.job.bootstrap.mcp;

import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 功能: MCP 客户端返回值类型（任务 G6）。
 *
 * 设计说明:
 * 1. 每次协议交换都保留原始请求/响应帧（McpExchange），后台连接器页可直接展示
 *    “真实发生的 JSON-RPC 帧”，而不是拼装的示意数据；
 * 2. 业务结果（握手信息/工具列表/工具输出文本）与协议帧分离，Agent 接入只用 result。
 */
public final class McpClientResults {

    private McpClientResults() {
    }

    /** 一次 MCP 协议交换：原始帧 + 业务结果。 */
    public record McpExchange<T>(String requestJson, String responseJson, T result) {
    }

    /** initialize 握手结果。 */
    public record McpHandshake(String protocolVersion, String serverName, String serverVersion) {
    }

    /** tools/list 结果（原始工具定义节点列表）。 */
    public record McpTools(List<JsonNode> tools) {
    }
}
