package com.job.bootstrap.agent.guardrail;

import com.job.bootstrap.agent.executor.AgentPlanExecutionResult;
import com.job.bootstrap.agent.executor.AgentPlanStepExecutionResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 作者:hfj
 * 功能:Agent 回复幻觉风险降级
 * 日期:2026/6/21
 */
@Component
@RequiredArgsConstructor
public class HallucinationGuard {

    private final PiiMasker piiMasker;

    /**
     * 清洗最终回复。
     *
     * 方法步骤:
     * 1. 先做 PII 脱敏，避免最终回复直接暴露手机号、邮箱、身份证、token。
     * 2. 如果 Executor 失败，但回复里出现“已完成/已生成/已找到”等成功表达，则替换为失败说明。
     * 3. 如果没有任何成功工具步骤，但回复声称“根据你的简历/岗位/沟通记录”，则增加依据不足提示。
     * 4. 第一版只做保守降级，不尝试重写复杂业务结论，避免误伤正常回答。
     */
    public String sanitizeFinalAnswer(String answer, AgentPlanExecutionResult executionResult) {
        String masked = piiMasker.maskText(answer);
        if (!StringUtils.hasText(masked)) {
            return masked;
        }

        if (executionResult != null
                && !Boolean.TRUE.equals(executionResult.getSuccess())
                && containsSuccessClaim(masked)) {
            return "本轮工具执行没有成功完成，不能把结果当作已完成任务。"
                    + "失败原因: "
                    + nullToDash(executionResult.getMessage())
                    + "\n\n你可以补充缺失信息或稍后重试。";
        }

        if (!hasSuccessfulToolStep(executionResult) && containsEvidenceClaim(masked)) {
            return "当前没有足够的工具执行结果支撑个性化结论，以下内容只能作为通用建议参考。\n\n" + masked;
        }

        return masked;
    }

    private boolean containsSuccessClaim(String answer) {
        return answer.contains("已完成")
                || answer.contains("已经完成")
                || answer.contains("已生成")
                || answer.contains("已经生成")
                || answer.contains("已找到")
                || answer.contains("已经找到")
                || answer.contains("匹配度为")
                || answer.contains("评分为");
    }

    private boolean containsEvidenceClaim(String answer) {
        return answer.contains("根据你的简历")
                || answer.contains("根据你的岗位")
                || answer.contains("根据你的沟通记录")
                || answer.contains("我查看了你的简历")
                || answer.contains("我已经读取");
    }

    private boolean hasSuccessfulToolStep(AgentPlanExecutionResult executionResult) {
        if (executionResult == null || CollectionUtils.isEmpty(executionResult.getSteps())) {
            return false;
        }

        List<AgentPlanStepExecutionResult> steps = executionResult.getSteps();
        for (AgentPlanStepExecutionResult step : steps) {
            if (step != null && "COMPLETED".equals(step.getStatus()) && step.getToolName() != null) {
                return true;
            }
        }
        return false;
    }

    private String nullToDash(Object value) {
        return value == null ? "-" : String.valueOf(value);
    }
}
