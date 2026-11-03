package com.job.bootstrap.agent.seek;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.job.bootstrap.agent.plan.AgentPlanParameterExtractor;
import com.job.bootstrap.agent.progress.TaskProgressPublisher;
import com.job.bootstrap.agent.task.TaskBudgetRegistry;
import com.job.bootstrap.agent.task.TaskExecutionBudget;
import com.job.bootstrap.agent.search.ReactSearchProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import com.job.bootstrap.agent.task.AgentTaskService;
import com.job.common.entity.agent.AgentPlan;
import com.job.enums.AgentPlanStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Map;

/**
 * 功能: 求职复合任务入口（第二阶段）。
 *
 * 路由规则:
 * 1. 携带 planId 且该计划的 runtimeMode 是 WORKFLOW_MULTI_AGENT → 按任务路由（开关不参与，
 *    旧任务不会被关开关丢下）；
 * 2. 新任务：开关开启 + 消息是复合意图（简历 + 岗位/匹配/差距/适合）→ 接管；
 * 3. 简单请求（只找岗位、只分析简历）不进入本入口，避免每个问题都跑三个 Agent。
 *
 * 事务边界: NOT_SUPPORTED + 聊天端非事务分发（会话/消息短事务提交后执行）；
 * 父计划与状态更新走 AgentTaskService 的短事务，模型/工具执行在事务外。
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

    /**
     * 是否复合求职意图（简历 + 岗位/匹配/差距）。
     */
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

    /**
     * 路由判断（供 chat/streamChat 统一分发）。
     */
    public boolean shouldHandle(String intentCode, Long planId, String message) {
        if (planId != null) {
            AgentPlan plan = agentTaskService.load(planId);
            if (plan != null && AgentTaskService.RUNTIME_MULTI_AGENT.equals(plan.getRuntimeMode())) {
                return true;
            }
        }
        return properties.isEnabled() && isComposite(message);
    }

    /**
     * 执行复合任务（父计划 + 三 Agent 协作）。
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public Result execute(Long userId, Long conversationId, Long planId, String traceId,
                          String message, boolean recover) {
        return execute(userId, conversationId, planId, traceId, message, recover, TaskProgressPublisher.NOOP);
    }

    /**
     * 执行复合任务（第三阶段：进度发布器 + 父任务共享预算）。
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public Result execute(Long userId, Long conversationId, Long planId, String traceId,
                          String message, boolean recover, TaskProgressPublisher publisher) {
        AgentPlan plan;
        if (planId != null) {
            plan = agentTaskService.loadOwned(userId, planId);
            if (plan == null) {
                return new Result(true, "任务不存在或不属于当前用户，已拒绝。", "REJECTED", planId, null);
            }
            if (!AgentTaskService.RUNTIME_MULTI_AGENT.equals(plan.getRuntimeMode())) {
                // 不属于本引擎的任务：交回上层路由（这里直接拒绝，避免错引擎执行）
                return new Result(false, null, "NOT_HANDLED", planId, null);
            }
        } else {
            // 新任务：固化 runtimeMode 与初始条件快照（开关只影响新任务）
            String snapshot = toJson(parameterExtractor.extract(message));
            plan = agentTaskService.createParentPlan(userId, conversationId, traceId,
                    "JOB_SEEK_FLOW", message, AgentTaskService.RUNTIME_MULTI_AGENT, snapshot);
        }

        // 父任务预算：补参恢复延续已用额度，但重置活动时间窗口（等待用户不计时）
        TaskExecutionBudget budget = taskBudgetRegistry.getOrCreate(plan.getId());
        budget.restore(
                plan.getModelCallsUsed() == null ? 0 : plan.getModelCallsUsed(),
                plan.getToolCallsUsed() == null ? 0 : plan.getToolCallsUsed(),
                0L);

        JobSeekWorkflow.JobSeekOutcome outcome = jobSeekWorkflow.execute(
                new JobSeekWorkflow.JobSeekInput(userId, conversationId, plan.getId(), traceId,
                        message, plan.getExtractedParamsJson()), publisher, budget);

        // 额度落库（补参/重试延续，不清零）
        agentTaskService.updateBudgetUsage(plan.getId(), budget.modelCallsUsed(), budget.toolCallsUsed());

        if (budget.isCancelled()) {
            agentTaskService.markCancelled(plan.getId(), budget.cancelReason());
            return new Result(true, "任务已取消，已完成的结果已保留。", "CANCELLED", plan.getId(),
                    AgentPlanStatus.CANCELLED.name());
        }

        AgentPlanStatus planStatus = switch (outcome.status()) {
            case "SUCCEEDED", "EMPTY" -> AgentPlanStatus.COMPLETED;
            case "NEED_INPUT" -> AgentPlanStatus.NEED_CLARIFICATION;
            default -> AgentPlanStatus.FAILED;
        };
        // 待补充问题落库：前端刷新后据此恢复“缺什么、能选什么”
        agentTaskService.updateStatus(plan.getId(), planStatus,
                "FAILED".equals(outcome.status()) ? outcome.answer() : null,
                "NEED_INPUT".equals(outcome.status()) ? outcome.question() : null);

        return new Result(true, outcome.answer(), outcome.status(), plan.getId(), planStatus.name());
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            return "{}";
        }
    }
}
