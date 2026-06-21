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
 * 严格限制Executor只能执行Planner预先批准的工具，防止工具越权调用
 * 日期:2026/6/21
 */
@Component
public class ToolAccessGuard {

    /***
     * 校验待执行工具是否在当前计划步骤的允许范围内
     *
     * @param plan Agent执行计划主对象（来自数据库，可信来源）
     * @param step 当前执行计划步骤对象（来自数据库，可信来源）
     * @param executableToolName 待执行的工具名称（来自Executor，不可信来源）
     */
    public void assertToolAllowedByPlan(AgentPlan plan, AgentPlanStep step, String executableToolName) {

        // 任何必要上下文缺失都直接拦截，防止空指针绕过检查
        // 安全设计：宁可误拦也不放过，缺失上下文意味着执行流程异常
        if (plan == null || step == null || !StringUtils.hasText(executableToolName)) {
            throw new AgentToolException(
                    AgentToolErrorCode.TOOL_GUARDRAIL_BLOCKED,
                    executableToolName,
                    "工具执行缺少计划上下文，已被 Guardrails 拦截"
            );
        }

        // 解析当前步骤允许执行的工具列表（从可信来源step.getToolName()解析）
        Set<String> allowedTools = parseAllowedTools(step.getToolName());

        // 严格匹配检查：工具名称必须完全一致
        // 安全设计：使用trim()处理可能的首尾空白，但不做任何其他转换
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

    //解析Planner生成的工具表达式，转换为允许执行的工具名称集合
    private Set<String> parseAllowedTools(String toolExpression) {
        // 空表达式返回空集合，表示不允许执行任何工具
        if (!StringUtils.hasText(toolExpression)) {
            return Set.of();
        }

        // 流式处理：分割→修剪→过滤→收集为不可变集合
        return Arrays.stream(toolExpression.split("/"))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .collect(Collectors.toSet());
    }
}
