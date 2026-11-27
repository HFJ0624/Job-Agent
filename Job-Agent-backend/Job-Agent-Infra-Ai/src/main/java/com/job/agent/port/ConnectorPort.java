package com.job.agent.port;

import java.util.Map;

/**
 * 功能: Agent 外部连接器执行端口（Infra-Ai 定义，Bootstrap 实现）。
 *
 * <p>工具名、参数协议与返回值保持原样（String JSON），由宿主接入现有 MCP 连接器实现。</p>
 */
public interface ConnectorPort {

    /**
     * 执行指定连接器工具。
     *
     * @param toolName 工具名（注册协议的一部分，不可更改）
     * @param params   参数
     * @return 工具输出 JSON
     */
    String invokeConnector(String toolName, Map<String, Object> params);
}
