package com.job.bootstrap.agent.guardrail;

import com.job.bootstrap.agent.executor.AgentPlanExecutionResult;
import com.job.common.agent.tool.AgentToolSchema;
import com.job.common.entity.agent.AgentPlan;
import com.job.common.entity.agent.AgentPlanStep;
import com.job.common.vo.agent.AgentGuardrailResult;

/**
 * 作者:hfj
 * 功能:Agent Guardrails 统一入口
 * 日期:2026/6/21
 */
public interface AgentGuardrailService {

    /**
     * 检查用户输入。
     */
    AgentGuardrailResult checkUserInput(Long userId, String message);

    /**
     * 校验工具执行权限和敏感操作。
     */
    void assertToolExecutionAllowed(AgentPlan plan, AgentPlanStep step, String toolName, AgentToolSchema schema);

    /**
     * 校验工具输出 JSON。
     */
    void validateToolOutput(String toolName, AgentToolSchema schema, String outputJson);

    /**
     * 对 Trace 或模型上下文做 PII 脱敏。
     */
    Object maskSensitiveData(Object value);

    /**
     * 清洗最终回复。
     */
    String sanitizeFinalAnswer(String answer, AgentPlanExecutionResult executionResult);
}
