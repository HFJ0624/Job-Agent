package com.job.mcp.protocol;

/**
 * 功能: MCP 协议异常（任务 G6）。
 *
 * 说明:
 * 1. 只承载“协议级”错误（未知方法/参数非法/未知工具），由 Handler 转成 JSON-RPC error 响应；
 * 2. 工具内部业务异常不走这里——tools/call 会包装成 isError=true 的结果返回给调用方。
 */
public class McpProtocolException extends RuntimeException {

    private final int code;

    public McpProtocolException(int code, String message) {
        super(message);
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
