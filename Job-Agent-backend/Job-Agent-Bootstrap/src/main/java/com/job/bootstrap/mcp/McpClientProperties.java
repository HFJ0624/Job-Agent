package com.job.bootstrap.mcp;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 功能: MCP 客户端配置（任务 G6）。
 *
 * 说明:
 * 1. baseUrl 默认指向本服务的 /mcp 端点——G6 v1 打通的是“自建 server + 自研客户端”的完整协议回路；
 * 2. 配置化后，连接外部 MCP server（VM/内网）只需改 baseUrl，客户端与 Agent 接入零改动；
 * 3. timeoutSeconds 覆盖 connect/read 超时，与模型调用超时分开治理。
 */
@Data
@ConfigurationProperties(prefix = "job.agent.mcp")
public class McpClientProperties {

    /** MCP 服务端地址（JSON-RPC over HTTP 端点）。 */
    private String baseUrl = "http://localhost:8500/mcp";

    /** 单次调用超时（秒）。 */
    private int timeoutSeconds = 30;
}
