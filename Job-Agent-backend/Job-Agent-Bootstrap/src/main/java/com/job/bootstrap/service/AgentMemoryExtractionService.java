package com.job.bootstrap.service;

import com.job.bootstrap.agent.executor.AgentPlanExecutionResult;
import com.job.common.entity.agent.AgentPlan;

/**
 * Agent 长期记忆提取服务接口。
 *
 * <p>核心职责：在 Agent 计划执行完成后，从执行结果中萃取可长期保存的结构化记忆，并触发记忆持久化。</p>
 *
 * <p>所属业务模块：AI 助手 - 长期记忆（Long-Term Memory）</p>
 *
 * <p>主要调用链：
 * AgentPlanExecutorService -&gt; AgentMemoryExtractionService -&gt; AgentMemoryExtractionServiceImpl -&gt; AiModelGatewayService / AgentMemoryService</p>
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
