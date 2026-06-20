package com.job.bootstrap.agent.plan;

import java.util.Map;

/**
 * 作者:hfj
 * 功能:Agent 计划步骤模板
 * 日期:2026/6/19
 */
public record AgentPlanStepTemplate(
        String stepName,
        String stepGoal,
        String toolName,
        Map<String, Object> toolInputSchema,
        String completionCriteria
) {
}
