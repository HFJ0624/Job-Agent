package com.job.bootstrap.agent.plan;

import java.util.List;

/**
 * 作者:hfj
 * 功能:Agent 计划模板
 * 日期:2026/6/19
 */
public record AgentPlanTemplate(
        String planTitle,
        String planSummary,
        List<String> requiredParams,
        List<AgentPlanStepTemplate> steps
) {
}
