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
     * 执行指定计划（默认不恢复失败计划）。
     *
     * @param userId 当前登录用户ID
     * @param planId 计划ID
     * @return 执行结果
     */
    AgentPlanExecutionResult executePlan(Long userId, Long planId);

    /**
     * 执行指定计划，并显式控制是否恢复上次失败的计划。
     *
     * 方法步骤:
     * 1. recover=false 时保持原有语义：已失败的计划直接返回历史结果，不重复执行。
     * 2. recover=true 时允许把 FAILED 计划中未完成的步骤重置后继续执行，
     *    已完成的步骤会直接采用历史结果，不会重复调用工具。
     * 3. 无论是否恢复，都需要先通过计划状态抢占，避免同一计划被并发重复执行。
     *
     * @param userId 当前登录用户ID
     * @param planId 计划ID
     * @param recover 是否允许恢复上次失败的计划
     * @return 执行结果
     */
    AgentPlanExecutionResult executePlan(Long userId, Long planId, boolean recover);
}
