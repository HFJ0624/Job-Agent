package com.job.bootstrap.agent.scenario.jobseek.entry;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.job.bootstrap.agent.scenario.jobseek.workflow.JobSeekWorkflow;
import com.job.bootstrap.agent.orchestration.plan.AgentPlanParameterExtractor;
import com.job.bootstrap.agent.runtime.progress.TaskProgressPublisher;
import com.job.bootstrap.agent.scenario.jobsearch.ReactSearchProperties;
import com.job.bootstrap.agent.orchestration.task.AgentTaskService;
import com.job.bootstrap.agent.runtime.budget.TaskBudgetRegistry;
import com.job.bootstrap.agent.runtime.budget.TaskExecutionBudget;
import com.job.common.entity.agent.AgentPlan;
import com.job.enums.AgentPlanStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 功能: 求职复合任务入口（第二阶段创建，第三阶段收口）。
 *
 * 收口要点（审查 F2/F3/F5）:
 * 1. **状态分发**：COMPLETED 直接复用已保存答复；RUNNING 拒绝重复执行；
 *    FAILED 仅在显式 retry 时重新领取；NEED_CLARIFICATION 合并补参继续；CANCELLED 是终态；
 * 2. **条件领取**：claimForExecution 用状态条件更新抢占执行权，并发重复请求只有一个能执行；
 * 3. **条件完成**：completeExecution 只有仍是执行中才写终态——取消/新执行不会被迟到结果覆盖；
 * 4. **任务上下文**：每轮把合并后的条件与所选简历写回父任务（{constraints, resumeId}），
 *    补参恢复先读上下文再叠加本轮修改；
 * 5. **预算**：恢复已用额度与已耗执行时间（等待用户不计时），结束后清理内存预算表。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@EnableConfigurationProperties(ReactSearchProperties.class)
public class JobSeekReactEntry {

    private final ReactSearchProperties properties;
    private final AgentTaskService agentTaskService;
    private final JobSeekWorkflow jobSeekWorkflow;
    private final AgentPlanParameterExtractor parameterExtractor;
    private final ObjectMapper objectMapper;
    private final TaskBudgetRegistry taskBudgetRegistry;

    /**
     * 入口结果。
     */
    public record Result(boolean handled, String answer, String status, Long planId, String planStatus) {
    }

    public boolean isComposite(String message) {
        if (!StringUtils.hasText(message)) {
            return false;
        }
        String text = message.toLowerCase();
        boolean hasResume = text.contains("简历") || text.contains("resume");
        boolean hasJob = text.contains("岗位") || text.contains("职位") || text.contains("job")
                || text.contains("匹配") || text.contains("差距") || text.contains("适合");
        return hasResume && hasJob;
    }

    public boolean shouldHandle(String intentCode, Long planId, String message) {
        if (planId != null) {
            AgentPlan plan = agentTaskService.load(planId);
            if (plan != null && AgentTaskService.RUNTIME_MULTI_AGENT.equals(plan.getRuntimeMode())) {
                return true;
            }
        }
        return properties.isEnabled() && isComposite(message);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public Result execute(Long userId, Long conversationId, Long planId, String traceId,
                          String message, boolean recover) {
        return execute(userId, conversationId, planId, traceId, message, recover, TaskProgressPublisher.NOOP);
    }

    /**
     * 执行复合任务（第三阶段收口：状态分发 + 条件领取 + 条件完成 + 上下文持久化）。
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public Result execute(Long userId, Long conversationId, Long planId, String traceId,
                          String message, boolean recover, TaskProgressPublisher publisher) {
        AgentPlan plan;
        String expectedStatus;
        if (planId != null) {
            plan = agentTaskService.loadOwned(userId, planId);
            if (plan == null) {
                return new Result(true, "任务不存在或不属于当前用户，已拒绝。", "REJECTED", planId, null);
            }
            if (!AgentTaskService.RUNTIME_MULTI_AGENT.equals(plan.getRuntimeMode())) {
                return new Result(false, null, "NOT_HANDLED", planId, null);
            }
            // 审查 F2：终态/执行中不重复执行
            String status = plan.getStatus();
            if (AgentPlanStatus.COMPLETED.name().equals(status)) {
                return new Result(true, answerOf(plan), AgentPlanStatus.COMPLETED.name(), plan.getId(), status);
            }
            if (AgentPlanStatus.CANCELLED.name().equals(status)) {
                return new Result(true, "该任务已取消，如需继续请发起新任务。",
                        AgentPlanStatus.CANCELLED.name(), plan.getId(), status);
            }
            if (AgentPlanStatus.RUNNING.name().equals(status)) {
                return new Result(true, "这个求职任务正在执行中，请稍候再试。", "RUNNING", plan.getId(), status);
            }
            if (AgentPlanStatus.FAILED.name().equals(status) && !recover) {
                return new Result(true, "上次执行失败：" + safe(plan.getFailReason()) + "。如需重试请显式重试。",
                        AgentPlanStatus.FAILED.name(), plan.getId(), status);
            }
            expectedStatus = status;
        } else {
            // 新任务：条件快照先以“上下文包装”格式写入，后续每轮覆盖
            String snapshot = toJson(context(null,
                    com.job.bootstrap.agent.scenario.jobsearch.SearchConstraints.merge(
                            parameterExtractor.extract(message), null)));
            plan = agentTaskService.createParentPlan(userId, conversationId, traceId,
                    "JOB_SEEK_FLOW", message, AgentTaskService.RUNTIME_MULTI_AGENT, snapshot);
            expectedStatus = AgentPlanStatus.PLANNED.name();
        }

        // 审查 F2：条件领取（并发重复请求只有一个能执行）
        if (!agentTaskService.claimForExecution(plan.getId(), expectedStatus)) {
            return new Result(true, "这个求职任务正在执行中，请稍候再试。", "RUNNING", plan.getId(), null);
        }

        TaskExecutionBudget budget = taskBudgetRegistry.getOrCreate(plan.getId());
        budget.restore(
                plan.getModelCallsUsed() == null ? 0 : plan.getModelCallsUsed(),
                plan.getToolCallsUsed() == null ? 0 : plan.getToolCallsUsed(),
                plan.getExecutionMillisUsed() == null ? 0L : plan.getExecutionMillisUsed());
        AgentPlan finalPlan = plan;

        try {
            JobSeekWorkflow.JobSeekOutcome outcome = jobSeekWorkflow.execute(
                    new JobSeekWorkflow.JobSeekInput(userId, conversationId, finalPlan.getId(), traceId,
                            message, finalPlan.getExtractedParamsJson(),
                            contextResumeId(finalPlan.getExtractedParamsJson())),
                    publisher, budget);

            // 额度与执行耗时落库（补参延续、等待不计时）
            agentTaskService.updateBudgetUsage(finalPlan.getId(), budget.modelCallsUsed(), budget.toolCallsUsed());
            agentTaskService.updateExecutionMillis(finalPlan.getId(), budget.usedMillis());

            // 审查 F3：合并后的条件与所选简历写回父任务，供下一轮补参使用
            /*
             * 审查修复（问题二）：写回上下文必须"合并"而不是覆盖。
             * 本次没给 resumeId（尚未选定简历）不能清空旧值；本次没算出条件
             * （理论上不应发生，这里兜底）也不能把历史条件写成 {}。
             */
            Map<String, Object> contextPayload = mergeContext(
                    finalPlan.getExtractedParamsJson(), outcome.resumeId(), outcome.constraints());
            agentTaskService.updateConstraints(finalPlan.getId(), toJson(contextPayload));

            // 审查 F5：取消优先——取消后不写成功终态
            if (budget.isCancelled()) {
                agentTaskService.completeExecution(finalPlan.getId(), AgentPlanStatus.CANCELLED,
                        "用户取消", null, null);
                return new Result(true, "任务已取消，已完成的结果已保留。", "CANCELLED",
                        finalPlan.getId(), AgentPlanStatus.CANCELLED.name());
            }

            AgentPlanStatus status = switch (outcome.status()) {
                case "SUCCEEDED", "EMPTY" -> AgentPlanStatus.COMPLETED;
                case "NEED_INPUT" -> AgentPlanStatus.NEED_CLARIFICATION;
                default -> AgentPlanStatus.FAILED;
            };
            boolean answerIsFinal = status == AgentPlanStatus.COMPLETED;
            boolean written = agentTaskService.completeExecution(finalPlan.getId(), status,
                    "FAILED".equals(outcome.status()) ? outcome.answer() : null,
                    "NEED_INPUT".equals(outcome.status()) ? outcome.question() : null,
                    answerIsFinal ? outcome.answer() : null);

            if (!written) {
                // 状态已被改变（取消或新执行接管）：不能把本次结果当成功返回
                AgentPlan latest = agentTaskService.loadOwned(userId, finalPlan.getId());
                String latestStatus = latest == null ? "UNKNOWN" : latest.getStatus();
                String answer = latest != null && AgentPlanStatus.COMPLETED.name().equals(latestStatus)
                        ? answerOf(latest)
                        : "任务状态已变更（" + latestStatus + "），本次结果未生效，请以任务状态为准。";
                return new Result(true, answer, latestStatus, finalPlan.getId(), latestStatus);
            }
            return new Result(true, outcome.answer(), status.name(), finalPlan.getId(), status.name());
        } catch (Exception exception) {
            log.warn("复合任务执行异常，planId={}, error={}", finalPlan.getId(), exception.getMessage());
            agentTaskService.completeExecution(finalPlan.getId(), AgentPlanStatus.FAILED,
                    "执行异常：" + exception.getMessage(), null, null);
            return new Result(true, "任务执行失败：" + exception.getMessage(), "FAILED",
                    finalPlan.getId(), AgentPlanStatus.FAILED.name());
        } finally {
            // 审查 F2：活动结束后清理内存预算，持久化状态作为恢复依据
            taskBudgetRegistry.release(finalPlan.getId());
        }
    }

    // 任务上下文包装：{constraints:{...}, resumeId:n}（历史兼容由 Workflow 负责）
    /**
     * 合并任务上下文：本次有效值优先，缺失时保留历史值。
     *
     * <p>兼容两种历史格式：{@code {constraints:{...}, resumeId:n}} 包装格式，
     * 以及早期直接保存 SearchConstraints 的平铺格式。</p>
     */
    private Map<String, Object> mergeContext(String existingJson, Long resumeId,
                                             com.job.bootstrap.agent.scenario.jobsearch.SearchConstraints constraints) {
        Map<String, Object> merged = new LinkedHashMap<>();
        Long previousResumeId = null;
        Object previousConstraints = null;

        if (StringUtils.hasText(existingJson)) {
            try {
                Map<String, Object> existing = objectMapper.readValue(existingJson,
                        objectMapper.getTypeFactory().constructMapType(Map.class, String.class, Object.class));
                if (existing.get("constraints") instanceof Map<?, ?> nested) {
                    previousConstraints = nested;
                    previousResumeId = existing.get("resumeId") instanceof Number number
                            ? number.longValue() : null;
                } else {
                    // 旧格式：整个对象就是条件快照
                    previousConstraints = existing;
                }
            } catch (Exception exception) {
                log.debug("任务上下文解析失败，按无历史处理，error={}", exception.getMessage());
            }
        }

        merged.put("resumeId", resumeId != null ? resumeId : previousResumeId);
        merged.put("constraints", constraints != null ? constraints
                : (previousConstraints == null ? Map.of() : previousConstraints));
        return merged;
    }

    private Map<String, Object> context(Long resumeId, com.job.bootstrap.agent.scenario.jobsearch.SearchConstraints constraints) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("resumeId", resumeId);
        context.put("constraints", constraints == null ? Map.of() : constraints);
        return context;
    }

    private Long contextResumeId(String json) {
        if (!StringUtils.hasText(json)) {
            return null;
        }
        try {
            Map<String, Object> map = objectMapper.readValue(json,
                    objectMapper.getTypeFactory().constructMapType(Map.class, String.class, Object.class));
            return map.get("resumeId") instanceof Number number ? number.longValue() : null;
        } catch (Exception exception) {
            return null;
        }
    }

    private String answerOf(AgentPlan plan) {
        return StringUtils.hasText(plan.getFinalAnswer())
                ? plan.getFinalAnswer()
                : "该任务已完成，结果见上方消息。";
    }

    private String safe(String text) {
        return StringUtils.hasText(text) ? text : "未知原因";
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            return "{}";
        }
    }
}
