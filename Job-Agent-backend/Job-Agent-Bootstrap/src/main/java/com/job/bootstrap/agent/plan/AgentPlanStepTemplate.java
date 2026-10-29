package com.job.bootstrap.agent.plan;

import java.util.Map;

/**
 * 作者:hfj
 * 功能:Agent 计划步骤模板
 * 日期:2026/6/19
 *
 * 设计说明（任务 J1 扩展）:
 * 1. stepParams 是“步骤级可执行参数”：声明这一步真正传给工具的入参，
 *    支持 {{step_N.output.xxx}} 占位符读取前序步骤的结构化输出（含数组下标）；
 * 2. 为 null 时沿用计划级 extractedParams（与历史行为一致，旧模板零改动）；
 * 3. 计划级参数为基、步骤级覆盖——主流程里 S3 可只声明 jobId 占位符，
 *    resumeId 等共享参数仍从计划级继承。
 */
public record AgentPlanStepTemplate(
        String stepName,
        String stepGoal,
        String toolName,
        Map<String, Object> toolInputSchema,
        String completionCriteria,
        Map<String, Object> stepParams
) {

    /** 兼容旧五参构造：无步骤级参数，沿用计划级 extractedParams。 */
    public AgentPlanStepTemplate(
            String stepName,
            String stepGoal,
            String toolName,
            Map<String, Object> toolInputSchema,
            String completionCriteria
    ) {
        this(stepName, stepGoal, toolName, toolInputSchema, completionCriteria, null);
    }
}
