package com.job.bootstrap.agent.runtime.executor;

/**
 * 功能: 执行批次被取代异常（任务 J2）。
 *
 * 说明:
 * 1. 触发条件：步骤回写时发现计划的 execution_epoch 已被其他执行器抢走
 *    （本执行器已被取代），条件更新影响 0 行；
 * 2. 语义：本批次立即放弃执行——工具还没调用的直接中止（零副作用），
 *    工具已执行的先保留结果再中止（见 preserveSupersededResult）；
 * 3. 由 AgentPlanExecutorServiceImpl 统一捕获：记观测事件 + 返回“已被取代”结果。
 */
public class ExecutionSupersededException extends RuntimeException {

    /** 被取代时正在处理的步骤序号（可能为 null，如终态回写阶段）。 */
    private final Integer stepNo;

    public ExecutionSupersededException(Integer stepNo) {
        super("执行批次已被取代，stepNo=" + stepNo);
        this.stepNo = stepNo;
    }

    public Integer getStepNo() {
        return stepNo;
    }
}
