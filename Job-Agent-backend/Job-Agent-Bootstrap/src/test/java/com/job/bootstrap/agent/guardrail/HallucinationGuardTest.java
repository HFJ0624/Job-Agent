package com.job.bootstrap.agent.guardrail;

import com.job.bootstrap.agent.executor.AgentPlanExecutionResult;
import com.job.bootstrap.agent.executor.AgentPlanStepExecutionResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * HallucinationGuard 单元测试。
 *
 * 覆盖重点:
 * 1. 执行失败但回复声称成功 -> 替换为明确的失败说明。
 * 2. 无成功工具步骤但回复声称基于用户数据 -> 增加依据不足提示前缀。
 * 3. 正常回复（有成功步骤、无违规声称）-> 直接返回脱敏后的原文。
 * 4. PII 脱敏：最终回复经过 PiiMasker 处理（手机号为代表）。
 * 5. 空文本直接返回空，不进入后续逻辑。
 */
class HallucinationGuardTest {

    private final PiiMasker piiMasker = mock(PiiMasker.class);
    private final HallucinationGuard guard = new HallucinationGuard(piiMasker);

    @Test
    void failedExecutionWithSuccessClaimShouldBeReplaced() {
        when(piiMasker.maskText("你的简历已生成评分报告")).thenReturn("你的简历已生成评分报告");
        AgentPlanExecutionResult result = AgentPlanExecutionResult.builder()
                .success(false)
                .message("解析失败")
                .steps(List.of())
                .build();

        String answer = guard.sanitizeFinalAnswer("你的简历已生成评分报告", result);

        assertThat(answer).contains("没有成功完成");
    }

    @Test
    void noSuccessfulStepWithEvidenceClaimShouldAddDisclaimer() {
        when(piiMasker.maskText("根据你的简历，我建议你补充实习经历")).thenReturn("根据你的简历，我建议你补充实习经历");
        AgentPlanExecutionResult result = AgentPlanExecutionResult.builder()
                .success(true)
                .steps(List.of(AgentPlanStepExecutionResult.builder()
                        .status("PENDING")
                        .toolName("ResumeAnalyzeTool.analyzeResume")
                        .build()))
                .build();

        String answer = guard.sanitizeFinalAnswer("根据你的简历，我建议你补充实习经历", result);

        assertThat(answer).startsWith("当前没有足够的工具执行结果");
        assertThat(answer).contains("根据你的简历，我建议你补充实习经历");
    }

    @Test
    void normalAnswerWithSuccessfulStepShouldPassThroughMasked() {
        when(piiMasker.maskText("今天天气不错")).thenReturn("今天天气不错");
        AgentPlanExecutionResult result = AgentPlanExecutionResult.builder()
                .success(true)
                .steps(List.of(AgentPlanStepExecutionResult.builder()
                        .status("COMPLETED")
                        .toolName("ResumeAnalyzeTool.analyzeResume")
                        .build()))
                .build();

        String answer = guard.sanitizeFinalAnswer("今天天气不错", result);

        assertThat(answer).isEqualTo("今天天气不错");
        verify(piiMasker).maskText("今天天气不错");
    }

    @Test
    void piiShouldBeMasked() {
        when(piiMasker.maskText("手机13800138000联系我")).thenReturn("手机138****8000联系我");

        String answer = guard.sanitizeFinalAnswer("手机13800138000联系我", null);

        assertThat(answer).contains("138****8000");
    }

    @Test
    void emptyAnswerShouldReturnEmpty() {
        when(piiMasker.maskText("")).thenReturn("");

        String answer = guard.sanitizeFinalAnswer("", null);

        assertThat(answer).isEmpty();
    }
}
