package com.job.bootstrap.service;

import com.job.common.vo.agent.AgentOperationDashboardVO;

/**
 * Admin Agent 运营看板服务。
 *
 * <p>核心职责：为管理员提供 Agent 体系的运营数据聚合看板，包括用户活跃度、任务执行量、行动项转化率等核心指标。</p>
 *
 * <p>所属业务模块：Agent 运营 / 后台管理</p>
 *
 * <p>主要调用链：Admin Controller → AdminAgentOperationService → 各 Agent 领域 Service / Mapper / 统计聚合层</p>
 */
public interface AdminAgentOperationService {

    /**
     * 查询 Agent 运营看板聚合数据。
     *
     * @return Agent 运营看板，包含核心指标、趋势图表、TOP 排行等数据
     */
    AgentOperationDashboardVO dashboard();
}
