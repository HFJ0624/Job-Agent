package com.job.bootstrap.agent.guardrail;

import com.job.common.entity.agent.AgentPlan;
import com.job.common.entity.agent.AgentPlanStep;
import com.job.enums.AgentToolErrorCode;
import com.job.exception.AgentToolException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ToolAccessGuard 单元测试。
 *
 * 覆盖重点:
 * 1. 上下文缺失拦截：plan / step 为 null，或待执行工具名为空 -> 抛 TOOL_GUARDRAIL_BLOCKED。
 * 2. 越权拦截：待执行工具名不在当前步骤允许列表（step.getToolName 解析）内 -> 拦截。
 * 3. 正常放行：工具名在允许列表内。
 * 4. 工具名前后空格按 trim 后匹配。
 * 5. 允许列表为空（step 工具名为空）时，任何工具都拦截。
 */
class ToolAccessGuardTest {

    private final ToolAccessGuard guard = new ToolAccessGuard();

    @Test
    void missingPlanContextShouldBeBlocked() {
        AgentPlanStep step = buildStep("ResumeAnalyzeTool.analyzeResume");
        assertThatThrownBy(() -> guard.assertToolAllowedByPlan(null, step, "ResumeAnalyzeTool.analyzeResume"))
                .isInstanceOf(AgentToolException.class)
                .extracting(error -> ((AgentToolException) error).getToolErrorCode())
                .isEqualTo(AgentToolErrorCode.TOOL_GUARDRAIL_BLOCKED);
    }

    @Test
    void missingStepContextShouldBeBlocked() {
        AgentPlan plan = buildPlan();
        assertThatThrownBy(() -> guard.assertToolAllowedByPlan(plan, null, "ResumeAnalyzeTool.analyzeResume"))
                .isInstanceOf(AgentToolException.class)
                .extracting(error -> ((AgentToolException) error).getToolErrorCode())
                .isEqualTo(AgentToolErrorCode.TOOL_GUARDRAIL_BLOCKED);
    }

    @Test
    void blankToolNameShouldBeBlocked() {
        AgentPlan plan = buildPlan();
        AgentPlanStep step = buildStep("ResumeAnalyzeTool.analyzeResume");
        assertThatThrownBy(() -> guard.assertToolAllowedByPlan(plan, step, "  "))
                .isInstanceOf(AgentToolException.class)
                .extracting(error -> ((AgentToolException) error).getToolErrorCode())
                .isEqualTo(AgentToolErrorCode.TOOL_GUARDRAIL_BLOCKED);
    }

    @Test
    void toolNotInAllowedListShouldBeBlocked() {
        AgentPlan plan = buildPlan();
        AgentPlanStep step = buildStep("ResumeAnalyzeTool.analyzeResume/JobMatchTool.matchJob");
        assertThatThrownBy(() -> guard.assertToolAllowedByPlan(plan, step, "GreetingGenerateTool.generateGreeting"))
                .isInstanceOf(AgentToolException.class)
                .extracting(error -> ((AgentToolException) error).getToolErrorCode())
                .isEqualTo(AgentToolErrorCode.TOOL_GUARDRAIL_BLOCKED);
    }

    @Test
    void toolInAllowedListShouldPass() {
        AgentPlan plan = buildPlan();
        AgentPlanStep step = buildStep("ResumeAnalyzeTool.analyzeResume/JobMatchTool.matchJob");
        guard.assertToolAllowedByPlan(plan, step, "ResumeAnalyzeTool.analyzeResume");
    }

    @Test
    void toolNameWithLeadingTrailingSpacesShouldMatchAfterTrim() {
        AgentPlan plan = buildPlan();
        AgentPlanStep step = buildStep("  ResumeAnalyzeTool.analyzeResume  ");
        guard.assertToolAllowedByPlan(plan, step, "ResumeAnalyzeTool.analyzeResume");
    }

    @Test
    void emptyAllowedListShouldBlockAnyTool() {
        AgentPlan plan = buildPlan();
        AgentPlanStep step = buildStep(null);
        assertThatThrownBy(() -> guard.assertToolAllowedByPlan(plan, step, "ResumeAnalyzeTool.analyzeResume"))
                .isInstanceOf(AgentToolException.class)
                .extracting(error -> ((AgentToolException) error).getToolErrorCode())
                .isEqualTo(AgentToolErrorCode.TOOL_GUARDRAIL_BLOCKED);
    }

    private AgentPlan buildPlan() {
        AgentPlan plan = new AgentPlan();
        plan.setId(1L);
        plan.setUserId(100L);
        return plan;
    }

    private AgentPlanStep buildStep(String toolName) {
        AgentPlanStep step = new AgentPlanStep();
        step.setId(10L);
        step.setPlanId(1L);
        step.setToolName(toolName);
        return step;
    }
}
