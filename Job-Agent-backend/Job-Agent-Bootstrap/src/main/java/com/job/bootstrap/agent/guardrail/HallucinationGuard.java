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
 * 在Agent生成最终回复后进行安全检查和修正，降低大模型幻觉带来的业务风险
 * 日期:2026/6/21
 */
@Component
@RequiredArgsConstructor
public class HallucinationGuard {

    //个人可识别信息脱敏工具
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

        // 1.优先进行PII脱敏，确保敏感信息不会出现在任何返回结果中
        String masked = piiMasker.maskText(answer);

        // 空文本直接返回，无需后续处理
        if (!StringUtils.hasText(masked)) {
            return masked;
        }

        // 2.执行状态校验 - 防止"执行失败但声称成功"的严重幻觉
        // 这是最高优先级的风险修正，因为会直接误导用户认为任务已完成
        if (executionResult != null
                && !Boolean.TRUE.equals(executionResult.getSuccess())
                && containsSuccessClaim(masked)) {
            return "本轮工具执行没有成功完成，不能把结果当作已完成任务。"
                    + "失败原因: "
                    + nullToDash(executionResult.getMessage())
                    + "\n\n你可以补充缺失信息或稍后重试。";
        }

        // 3.依据充分性校验 - 防止"无依据声称基于用户数据"的中度幻觉
        // 这种幻觉会让用户误以为Agent真的查看了他们的个人信息
        if (!hasSuccessfulToolStep(executionResult) && containsEvidenceClaim(masked)) {
            return "当前没有足够的工具执行结果支撑个性化结论，以下内容只能作为通用建议参考。\n\n" + masked;
        }

        // 所有检查通过，返回脱敏后的原始回复
        return masked;
    }

    /***
     * 检查回复中是否包含"任务已成功完成"类的表述
     *
     * @param answer Agent生成的回复文本
     * @return true表示包含成功声称，false表示不包含
     */
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

    /***
     * 检查回复中是否包含"基于用户特定数据"的表述
     *
     * @param answer Agent生成的回复文本
     * @return true表示包含成功声称，false表示不包含
     */
    private boolean containsEvidenceClaim(String answer) {
        return answer.contains("根据你的简历")
                || answer.contains("根据你的岗位")
                || answer.contains("根据你的沟通记录")
                || answer.contains("我查看了你的简历")
                || answer.contains("我已经读取");
    }

    /***
     * 检查Agent执行计划中是否有至少一个成功完成的工具调用步骤
     *
     * @param executionResult Agent工具计划的执行结果
     * @return true表示有成功的工具执行步骤，false表示没有
     */
    private boolean hasSuccessfulToolStep(AgentPlanExecutionResult executionResult) {

        // 执行结果为空或没有任何步骤，直接返回false
        if (executionResult == null || CollectionUtils.isEmpty(executionResult.getSteps())) {
            return false;
        }

        List<AgentPlanStepExecutionResult> steps = executionResult.getSteps();
        for (AgentPlanStepExecutionResult step : steps) {
            // 必须同时满足：步骤不为空、状态为已完成、有实际调用的工具名称
            if (step != null && "COMPLETED".equals(step.getStatus()) && step.getToolName() != null) {
                return true;
            }
        }

        // 遍历所有步骤后没有找到成功的工具调用
        return false;
    }

    //空值处理工具方法
    private String nullToDash(Object value) {
        return value == null ? "-" : String.valueOf(value);
    }
}
