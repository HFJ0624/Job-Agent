package com.job.bootstrap.service;

import com.job.common.vo.agent.AgentOperationDashboardVO;

/**
 * Admin Agent 运营看板服务。
 */
public interface AdminAgentOperationService {

    /**
     * 查询 Agent 运营看板。
     */
    AgentOperationDashboardVO dashboard();
}
