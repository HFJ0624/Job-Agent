package com.job.bootstrap.mcp;

/**
 * 功能: MCP 客户端协议异常（任务 G6）。
 *
 * 说明:
 * 1. 只表示“协议链路”失败：服务端返回 JSON-RPC error、网络异常、无响应等；
 * 2. code 透传服务端错误码（-32601/-32602 等），调用方可按码决策；-1 表示本地网络/解析失败；
 * 3. 工具业务错误（isError=true）不走这里，由 McpClient 映射为 AgentToolException。
 */
public class McpClientException extends RuntimeException {

    /** 服务端 JSON-RPC 错误码；-1 表示客户端侧（网络/解析）失败。 */
    private final int code;

    public McpClientException(int code, String message) {
        super(message);
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
