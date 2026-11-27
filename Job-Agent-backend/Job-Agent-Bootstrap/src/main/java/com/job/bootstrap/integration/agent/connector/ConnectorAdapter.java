package com.job.bootstrap.integration.agent.connector;

import com.job.agent.port.ConnectorPort;
import com.job.bootstrap.application.admin.service.AdminExternalConnectorService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

/** 功能: Agent 连接器端口的宿主实现，委托现有 AdminExternalConnectorService（MCP）。 */
@Component
@RequiredArgsConstructor
public class ConnectorAdapter implements ConnectorPort {

    private final AdminExternalConnectorService adminExternalConnectorService;

    @Override
    public String invokeConnector(String toolName, Map<String, Object> params) {
        return adminExternalConnectorService.invokeConnector(toolName, params);
    }
}
