package com.job.bootstrap.service;

import com.job.bootstrap.agent.executor.AgentPlanExecutionResult;

/**
 * Agent 计划执行服务接口。
 *
 * <p>核心职责：驱动 Agent 已生成计划的逐步执行，协调工具调用、状态流转、异常处理及执行结果收集。</p>
 *
 * <p>所属业务模块：AI 助手 - 计划执行（Execution）</p>
 *
 * <p>主要调用链：
 * AgentChatService / AdminAgentPlanService -&gt; AgentPlanExecutorService -&gt; AgentPlanExecutorServiceImpl -&gt; ToolRegistry / AgentTraceService / AgentMemoryExtractionService</p>
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
