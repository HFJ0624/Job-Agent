package com.job.bootstrap.agent.guardrail.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.job.bootstrap.agent.executor.AgentPlanExecutionResult;
import com.job.bootstrap.agent.guardrail.AgentGuardrailService;
import com.job.bootstrap.agent.guardrail.HallucinationGuard;
import com.job.bootstrap.agent.guardrail.PiiMasker;
import com.job.bootstrap.agent.guardrail.PromptInjectionGuard;
import com.job.bootstrap.agent.guardrail.SensitiveOperationGuard;
import com.job.bootstrap.agent.guardrail.ToolAccessGuard;
import com.job.bootstrap.agent.guardrail.ToolOutputJsonValidator;
import com.job.common.agent.tool.AgentToolSchema;
import com.job.common.entity.agent.AgentPlan;
import com.job.common.entity.agent.AgentPlanStep;
import com.job.common.vo.agent.AgentGuardrailResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 作者:hfj
 * 功能:Agent Guardrails 统一实现
 * 日期:2026/6/21
 */
@Service
@RequiredArgsConstructor
public class AgentGuardrailServiceImpl implements AgentGuardrailService {

    private final PromptInjectionGuard promptInjectionGuard;
    private final ToolAccessGuard toolAccessGuard;
    private final SensitiveOperationGuard sensitiveOperationGuard;
    private final ToolOutputJsonValidator toolOutputJsonValidator;
    private final HallucinationGuard hallucinationGuard;
    private final PiiMasker piiMasker;
    private final ObjectMapper objectMapper;

    /**
     * 检查用户输入。
     *
     * 方法步骤:
     * 1. 当前第一版先检查 Prompt 注入和越狱指令。
     * 2. 检查结果统一返回 AgentGuardrailResult。
     * 3. 调用方根据 action 决定继续 Planner，还是直接返回安全提示。
     */
    @Override
    public AgentGuardrailResult checkUserInput(Long userId, String message) {
        return promptInjectionGuard.check(message);
    }

    /**
     * 校验工具执行。
     *
     * 方法步骤:
     * 1. 先校验实际工具是否属于当前计划步骤允许的工具表达式。
     * 2. 再校验副作用工具和敏感操作是否满足用户确认要求。
     * 3. 两类检查都抛 AgentToolException，让 Executor 统一记录失败步骤。
     */
    @Override
    public void assertToolExecutionAllowed(
            AgentPlan plan,
            AgentPlanStep step,
            String toolName,
            AgentToolSchema schema
    ) {
        toolAccessGuard.assertToolAllowedByPlan(plan, step, toolName);
        sensitiveOperationGuard.assertSensitiveOperationAllowed(schema);
    }

    /**
     * 校验工具输出 JSON。
     */
    @Override
    public void validateToolOutput(String toolName, AgentToolSchema schema, String outputJson) {
        toolOutputJsonValidator.validate(toolName, schema, outputJson);
    }

    /**
     * 脱敏 Trace 或上下文对象。
     *
     * 方法步骤:
     * 1. 优先把普通 Java Bean 转成 Map/List 基础结构。
     * 2. 再递归脱敏手机号、邮箱、身份证、token、password。
     * 3. 如果转换失败，退回到字符串脱敏，保证 Trace 不因为脱敏失败影响主流程。
     */
    @Override
    public Object maskSensitiveData(Object value) {
        if (value == null) {
            return null;
        }

        try {
            Object normalized = objectMapper.convertValue(value, Object.class);
            return piiMasker.maskObject(normalized);
        } catch (Exception exception) {
            return piiMasker.maskText(String.valueOf(value));
        }
    }

    /**
     * 清洗最终回复。
     */
    @Override
    public String sanitizeFinalAnswer(String answer, AgentPlanExecutionResult executionResult) {
        return hallucinationGuard.sanitizeFinalAnswer(answer, executionResult);
    }
}
