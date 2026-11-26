package com.job.mcp.protocol;

/**
 * 功能: MCP（Model Context Protocol）JSON-RPC 错误码常量（任务 G6）。
 *
 * 说明:
 * 1. MCP 复用 JSON-RPC 2.0 错误码，这里集中定义，避免魔法数字散落；
 * 2. 服务端（McpProtocolHandler）与客户端（Bootstrap 的 McpClient）共用同一套语义，
 *    客户端按 code 映射异常类型，调用方无需解析 message。
 */
public final class McpErrorCodes {

    /** JSON 解析失败。 */
    public static final int PARSE_ERROR = -32700;

    /** 不是合法的 JSON-RPC 请求对象。 */
    public static final int INVALID_REQUEST = -32600;

    /** 方法不存在。 */
    public static final int METHOD_NOT_FOUND = -32601;

    /** 参数非法（含未知工具名）。 */
    public static final int INVALID_PARAMS = -32602;

    /** 服务端内部错误。 */
    public static final int INTERNAL_ERROR = -32603;

    private McpErrorCodes() {
    }
}
