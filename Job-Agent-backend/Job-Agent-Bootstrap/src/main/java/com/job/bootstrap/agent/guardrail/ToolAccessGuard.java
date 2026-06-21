package com.job.bootstrap.agent.guardrail;

import com.job.common.entity.agent.AgentPlan;
import com.job.common.entity.agent.AgentPlanStep;
import com.job.enums.AgentToolErrorCode;
import com.job.exception.AgentToolException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 作者:hfj
 * 功能:工具越权检查
 * 日期:2026/6/21
 */
@Component
public class ToolAccessGuard {

    /**
     * 校验工具是否属于当前计划步骤。
     *
     * 方法步骤:
     * 1. 读取 step.toolName，它是 Planner 写入数据库的允许工具表达式。
     * 2. 如果表达式是单个工具，必须和实际执行工具完全一致。
     * 3. 如果表达式是 "A / B"，说明 Planner 允许 Executor 按规则二选一。
     * 4. 实际工具不在允许列表中时直接抛错，防止模型或代码路径越权调用计划外工具。
     */
    public void assertToolAllowedByPlan(AgentPlan plan, AgentPlanStep step, String executableToolName) {
        if (plan == null || step == null || !StringUtils.hasText(executableToolName)) {
            throw new AgentToolException(
                    AgentToolErrorCode.TOOL_GUARDRAIL_BLOCKED,
                    executableToolName,
                    "工具执行缺少计划上下文，已被 Guardrails 拦截"
            );
        }

        Set<String> allowedTools = parseAllowedTools(step.getToolName());
        if (!allowedTools.contains(executableToolName.trim())) {
            throw new AgentToolException(
                    AgentToolErrorCode.TOOL_GUARDRAIL_BLOCKED,
                    executableToolName,
                    "工具不在当前计划步骤允许范围内，planId="
                            + plan.getId()
                            + ", stepId="
                            + step.getId()
            );
        }
    }

    private Set<String> parseAllowedTools(String toolExpression) {
        if (!StringUtils.hasText(toolExpression)) {
            return Set.of();
        }

        return Arrays.stream(toolExpression.split("/"))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .collect(Collectors.toSet());
    }
}
