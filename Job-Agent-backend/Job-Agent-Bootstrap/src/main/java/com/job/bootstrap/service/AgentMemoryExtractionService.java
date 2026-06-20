package com.job.bootstrap.service;

import com.job.bootstrap.agent.executor.AgentPlanExecutionResult;
import com.job.common.entity.agent.AgentPlan;

/**
 * 作者:hfj
 * 功能:Agent 长期记忆提取服务
 * 日期:2026/6/20
 */
public interface AgentMemoryExtractionService {

    /**
     * 从一次计划执行结果中提取可长期保存的记忆。
     *
     * @param plan Agent 计划
     * @param executionResult 计划执行结果
     */
    void extractFromExecution(AgentPlan plan, AgentPlanExecutionResult executionResult);
}
