package com.job.bootstrap.service;

import com.job.bootstrap.agent.executor.AgentPlanExecutionResult;

/**
 * 作者:hfj
 * 功能:Agent 计划执行服务
 * 日期:2026/6/20
 */
public interface AgentPlanExecutorService {

    /**
     * 执行指定计划。
     *
     * @param userId 当前登录用户ID
     * @param planId 计划ID
     * @return 执行结果
     */
    AgentPlanExecutionResult executePlan(Long userId, Long planId);
}
