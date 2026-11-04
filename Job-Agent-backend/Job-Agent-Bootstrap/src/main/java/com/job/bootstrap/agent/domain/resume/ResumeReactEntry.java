package com.job.bootstrap.agent.domain.resume;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.job.bootstrap.agent.runtime.artifact.AgentArtifactService;
import com.job.bootstrap.agent.orchestration.plan.AgentPlanParameterExtractor;
import com.job.bootstrap.agent.runtime.progress.AgentProgressEvent;
import com.job.bootstrap.agent.runtime.progress.TaskProgressPublisher;
import com.job.bootstrap.agent.domain.job.ReactSearchProperties;
import com.job.bootstrap.agent.orchestration.task.AgentTaskService;
import com.job.bootstrap.agent.runtime.budget.TaskBudgetRegistry;
import com.job.bootstrap.agent.runtime.budget.TaskExecutionBudget;
import com.job.common.entity.agent.AgentPlan;
import com.job.common.entity.resume.JobResume;
import com.job.enums.AgentPlanStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 功能: 独立简历分析入口（第三阶段）。
 *
 * 设计说明:
 * 1. 只做"薄入口 + 薄流程"：复用 ResumeAgentNode（同一套 ReAct 内核、
 *    同一份 ResumeProfile 契约与证据回溯校验），不复制 Prompt/产物契约；
 * 2. 任务身份：父计划 runtimeMode=WORKFLOW_RESUME，planId 作为稳定标识返回；
 * 3. **状态机与复合入口一致**（P0 修复）：
 *    COMPLETED 直接返回已保存答复；RUNNING 拒绝重复执行；FAILED 仅显式重试；
 *    NEED_CLARIFICATION 合并补参继续；CANCELLED 为终态；
 *    领取用 claimForExecution（条件更新），写回用 completeExecution（仅 RUNNING 可写），
 *    避免重复执行与"取消后被成功覆盖"。
 * 4. 输出：简历事实（带原文依据）+ 已有优势 + 信息缺口 + 建议，事实与建议分开。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@EnableConfigurationProperties(ReactSearchProperties.class)
public class ResumeReactEntry {

    public static final String RUNTIME_RESUME = "WORKFLOW_RESUME";

    private final ReactSearchProperties properties;
    private final AgentTaskService agentTaskService;
    private final ResumeAgentNode resumeAgentNode;
    private final AgentArtifactService agentArtifactService;
    private final AgentPlanParameterExtractor parameterExtractor;
    private final TaskBudgetRegistry taskBudgetRegistry;
    private final ResumeSelectionService resumeSelectionService;
    private final ObjectMapper objectMapper;

    /** 入口结果。 */
    public record Result(boolean handled, String answer, String status, Long planId, String planStatus) {
    }

    public boolean shouldHandle(String intentCode, Long planId, String message) {
        if (planId != null) {
            AgentPlan plan = agentTaskService.load(planId);
            if (plan != null && RUNTIME_RESUME.equals(plan.getRuntimeMode())) {
                return true;
            }
        }
        return properties.isEnabled() && "RESUME_ANALYZE".equals(intentCode);
    }

    /** 兼容旧调用（非显式重试、无进度发布）。 */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public Result execute(Long userId, Long conversationId, Long planId, String traceId,
                          String message, TaskProgressPublisher publisher) {
        return execute(userId, conversationId, planId, traceId, message, false, publisher);
    }

    /**
     * 执行简历分析（状态机 + 条件领取 + 条件写回）。
     *
     * @param recover 显式重试（仅对 FAILED 生效）
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public Result execute(Long userId, Long conversationId, Long planId, String traceId,
                          String message, boolean recover, TaskProgressPublisher publisher) {
        TaskProgressPublisher progress = publisher == null ? TaskProgressPublisher.NOOP : publisher;

        // 1. 任务身份与状态分发
        AgentPlan plan;
        String expectedStatus;
        if (planId != null) {
            plan = agentTaskService.loadOwned(userId, planId);
            if (plan == null) {
                return new Result(true, "任务不存在或不属于当前用户，已拒绝。", "REJECTED", planId, null);
            }
            if (!RUNTIME_RESUME.equals(plan.getRuntimeMode())) {
                return new Result(false, null, "NOT_HANDLED", planId, null);
            }
            String status = plan.getStatus();
            if (AgentPlanStatus.COMPLETED.name().equals(status)) {
                return new Result(true, answerOf(plan), AgentPlanStatus.COMPLETED.name(), plan.getId(), status);
            }
            if (AgentPlanStatus.CANCELLED.name().equals(status)) {
                return new Result(true, "该任务已取消，如需继续请发起新任务。",
                        AgentPlanStatus.CANCELLED.name(), plan.getId(), status);
            }
            if (AgentPlanStatus.RUNNING.name().equals(status)) {
                return new Result(true, "简历分析正在执行中，请稍候再试。", "RUNNING", plan.getId(), status);
            }
            if (AgentPlanStatus.FAILED.name().equals(status) && !recover) {
                return new Result(true, "上次分析失败：" + safe(plan.getFailReason()) + "。如需重试请显式重试。",
                        AgentPlanStatus.FAILED.name(), plan.getId(), status);
            }
            expectedStatus = status;
        } else {
            String snapshot = toJson(parameterExtractor.extract(message));
            plan = agentTaskService.createParentPlan(userId, conversationId, traceId,
                    "RESUME_ANALYZE", message, RUNTIME_RESUME, snapshot);
            expectedStatus = AgentPlanStatus.PLANNED.name();
        }

        // 2. 条件领取：并发重复请求只有一个能执行
        if (!agentTaskService.claimForExecution(plan.getId(), expectedStatus)) {
            return new Result(true, "简历分析正在执行中，请稍候再试。", "RUNNING", plan.getId(), null);
        }

        TaskExecutionBudget budget = taskBudgetRegistry.getOrCreate(plan.getId());
        budget.restore(plan.getModelCallsUsed() == null ? 0 : plan.getModelCallsUsed(),
                plan.getToolCallsUsed() == null ? 0 : plan.getToolCallsUsed(),
                plan.getExecutionMillisUsed() == null ? 0L : plan.getExecutionMillisUsed());
        AgentPlan running = plan;

        try {
            // 3. 简历定位（显式 > 名称 > 默认 > 唯一；多份无法确定 → 暂停等待选择）
            Map<String, Object> explicit = parameterExtractor.extract(message);
            // 简历定位统一走 ResumeSelectionService（P1）
            ResumeSelectionService.Selection selection =
                    resumeSelectionService.select(userId, explicit, null, "分析简历");
            JobResume resume = selection.resume();
            if (selection.needsInput()) {
                String question = selection.question();
                agentTaskService.completeExecution(running.getId(),
                        AgentPlanStatus.NEED_CLARIFICATION, null, question, null);
                progress.publish(new AgentProgressEvent(AgentProgressEvent.WAITING_INPUT,
                        running.getId(), null, "RESUME_AGENT", "RESUME", 0, "WAITING_INPUT", question,
                        System.currentTimeMillis()));
                return new Result(true, question, "NEED_INPUT", running.getId(),
                        AgentPlanStatus.NEED_CLARIFICATION.name());
            }

            progress.publish(new AgentProgressEvent(AgentProgressEvent.TASK_STARTED,
                    running.getId(), null, null, null, 0, "RUNNING", "开始分析简历", System.currentTimeMillis()));
            progress.publish(new AgentProgressEvent(AgentProgressEvent.NODE_STARTED,
                    running.getId(), null, "RESUME_AGENT", "RESUME", 0, "RUNNING", "正在分析简历",
                    System.currentTimeMillis()));

            ResumeAgentNode.Result result = resumeAgentNode.execute(userId, conversationId, running.getId(), traceId,
                    resume, resumeVersion(resume), text(explicit.get("keyword")), budget);

            agentTaskService.updateBudgetUsage(running.getId(), budget.modelCallsUsed(), budget.toolCallsUsed());
            agentTaskService.updateExecutionMillis(running.getId(), budget.usedMillis());

            // 4. 取消优先：取消后不写 FAILED/COMPLETED
            if (budget.isCancelled()) {
                agentTaskService.completeExecution(running.getId(),
                        AgentPlanStatus.CANCELLED, "用户取消", null, null);
                return new Result(true, "任务已取消，已完成的结果已保留。", "CANCELLED",
                        running.getId(), AgentPlanStatus.CANCELLED.name());
            }

            if (result.profile() == null) {
                agentTaskService.completeExecution(running.getId(), AgentPlanStatus.FAILED,
                        result.reasonCode(), null, null);
                progress.publish(new AgentProgressEvent(AgentProgressEvent.TASK_FAILED,
                        running.getId(), result.runId(), "RESUME_AGENT", "RESUME", 0, "FAILED",
                        "简历分析失败", System.currentTimeMillis()));
                return new Result(true, "简历分析失败（" + result.reasonCode() + "），可稍后重试。",
                        "FAILED", running.getId(), AgentPlanStatus.FAILED.name());
            }

            // Node 要求补充信息：父任务进入等待，不写成功
            if ("NEED_INPUT".equals(result.profile().outcome())) {
                String question = StringUtils.hasText(result.profile().question())
                        ? result.profile().question() : "请确认要分析的简历或补充目标方向。";
                agentTaskService.completeExecution(running.getId(),
                        AgentPlanStatus.NEED_CLARIFICATION, null, question, null);
                progress.publish(new AgentProgressEvent(AgentProgressEvent.WAITING_INPUT,
                        running.getId(), result.runId(), "RESUME_AGENT", "RESUME", 0, "WAITING_INPUT",
                        question, System.currentTimeMillis()));
                return new Result(true, question, "NEED_INPUT", running.getId(),
                        AgentPlanStatus.NEED_CLARIFICATION.name());
            }

            // 5. 画像产物 + 答复
            agentArtifactService.save(userId, running.getId(), result.runId(),
                    AgentArtifactService.TYPE_RESUME_PROFILE, result.profile().resumeVersion(),
                    toJson(result.profile()), "resumeId=" + resume.getId());

            String answer = buildAnswer(result.profile(), resume);
            boolean written = agentTaskService.completeExecution(running.getId(),
                    AgentPlanStatus.COMPLETED, null, null, answer);
            if (!written) {
                AgentPlan latest = agentTaskService.loadOwned(userId, running.getId());
                String latestStatus = latest == null ? "UNKNOWN" : latest.getStatus();
                String latestAnswer = latest != null && AgentPlanStatus.COMPLETED.name().equals(latestStatus)
                        ? answerOf(latest)
                        : "任务状态已变更（" + latestStatus + "），本次结果未生效，请以任务状态为准。";
                return new Result(true, latestAnswer, latestStatus, running.getId(), latestStatus);
            }

            progress.publish(new AgentProgressEvent(AgentProgressEvent.NODE_COMPLETED,
                    running.getId(), result.runId(), "RESUME_AGENT", "RESUME", 0, "SUCCEEDED",
                    "简历分析完成", System.currentTimeMillis()));
            progress.publish(new AgentProgressEvent(AgentProgressEvent.TASK_COMPLETED,
                    running.getId(), null, null, null, 0, "SUCCEEDED", "任务完成", System.currentTimeMillis()));

            return new Result(true, answer, "SUCCEEDED", running.getId(), AgentPlanStatus.COMPLETED.name());
        } catch (Exception exception) {
            log.warn("简历分析执行异常，planId={}, error={}", running.getId(), exception.getMessage());
            agentTaskService.completeExecution(running.getId(), AgentPlanStatus.FAILED,
                    "执行异常：" + exception.getMessage(), null, null);
            return new Result(true, "简历分析失败：" + exception.getMessage(), "FAILED",
                    running.getId(), AgentPlanStatus.FAILED.name());
        } finally {
            taskBudgetRegistry.release(running.getId());
        }
    }

    // 简历事实 + 证据 + 缺口 + 建议（事实与建议分开，不把“未见证据”说成“不会”）
    private String buildAnswer(ResumeProfile profile, JobResume resume) {
        StringBuilder builder = new StringBuilder();
        builder.append("《").append(resume.getResumeName()).append("》的分析结果：\n\n");

        builder.append("【已有事实】\n");
        if (!profile.skills().isEmpty()) {
            builder.append("技能：").append(String.join("、", profile.skills())).append("\n");
        }
        if (!profile.experiences().isEmpty()) {
            builder.append("经历：").append(String.join("；", limit(profile.experiences(), 3))).append("\n");
        }
        if (!profile.evidence().isEmpty()) {
            builder.append("原文依据：");
            builder.append(String.join("；", profile.evidence().stream()
                    .limit(3)
                    .map(e -> e.sectionRef() == null ? "「" + e.quote() + "」"
                            : e.sectionRef() + "「" + e.quote() + "」")
                    .toList()));
            builder.append("\n");
        }

        if (!profile.missingInformation().isEmpty() || !profile.uncertainties().isEmpty()) {
            builder.append("\n【简历中未见信息】\n");
            List<String> gaps = new ArrayList<>(profile.missingInformation());
            gaps.addAll(profile.uncertainties());
            builder.append(String.join("；", limit(gaps, 5))).append("\n");
        }

        builder.append("\n【建议】\n");
        if (!profile.missingInformation().isEmpty()) {
            builder.append("- 补充简历中缺少的信息（如量化指标、项目结果），提升可信度\n");
        }
        if (!profile.skills().isEmpty()) {
            builder.append("- 围绕 ").append(String.join("、", limit(profile.skills(), 3)))
                    .append(" 准备 1-2 个可讲的项目案例\n");
        }
        builder.append("- 如需岗位匹配分析，可以告诉我目标城市和方向。");
        return builder.toString();
    }


    private String answerOf(AgentPlan plan) {
        return StringUtils.hasText(plan.getFinalAnswer())
                ? plan.getFinalAnswer()
                : "该任务已完成，结果见上方消息。";
    }

    private String safe(String text) {
        return StringUtils.hasText(text) ? text : "未知原因";
    }

    private String resumeVersion(JobResume resume) {
        return String.valueOf(resume.getId()) + ":" + (resume.getUpdateTime() == null ? "" :
                resume.getUpdateTime().getTime());
    }

    private List<String> limit(List<String> values, int max) {
        return values.size() > max ? values.subList(0, max) : values;
    }

    private String text(Object value) {
        return value == null ? null : String.valueOf(value).trim();
    }

    private Long longOf(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && StringUtils.hasText(text)) {
            try {
                return Long.parseLong(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            return "{}";
        }
    }
}
