package com.job.bootstrap.agent.compensation;

import com.job.bootstrap.agent.executor.AgentPlanStepExecutionResult;

/**
 * 功能: 计划步骤补偿动作（任务 G9）。
 *
 * 设计说明:
 * 1. 一个工具一个动作 bean，Spring 会把所有实现注入 AgentCompensationRegistry，
 *    执行器终失败时按 stepNo 逆序调用——这是进程内 Saga 的“补偿”半边；
 * 2. compensate 必须幂等：补偿可能被重试（方案②的升级方向），
 *    重复关闭一条已关闭的记录不能报错；
 * 3. 补偿失败抛异常即可，由 AgentPlanCompensator 统一记录失败事件并继续后续补偿——
 *    单步补偿失败不能阻断其他步骤的补偿；
 * 4. 入参只给执行结果（含工具输出 JSON），不传实体——补偿是运维性质动作，
 *    不需要为它扩展业务查询接口。
 */
public interface AgentCompensationAction {

    /** 动作对应的工具名（与 Agent 工具名一致）。 */
    String toolName();

    /** 补偿类型。 */
    CompensationKind kind();

    /**
     * 执行补偿。
     *
     * @param userId     用户 ID
     * @param planId     计划 ID（用于日志关联）
     * @param stepResult 本轮该步骤的执行结果（工具输出 JSON 从 toolResult.dataJson 取）
     */
    void compensate(Long userId, Long planId, AgentPlanStepExecutionResult stepResult);
}
