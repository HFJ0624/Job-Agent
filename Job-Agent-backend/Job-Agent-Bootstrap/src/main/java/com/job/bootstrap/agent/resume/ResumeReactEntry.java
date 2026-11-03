package com.job.bootstrap.agent.resume;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.job.bootstrap.agent.artifact.AgentArtifactService;
import com.job.bootstrap.agent.plan.AgentPlanParameterExtractor;
import com.job.bootstrap.agent.progress.TaskProgressPublisher;
import com.job.bootstrap.agent.search.ReactSearchProperties;
import com.job.bootstrap.agent.task.AgentTaskService;
import com.job.bootstrap.agent.task.TaskBudgetRegistry;
import com.job.bootstrap.agent.task.TaskExecutionBudget;
import com.job.bootstrap.service.JobResumeService;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 功能: 独立简历分析入口（第三阶段）。
 *
 * 设计说明:
 * 1. 只做“薄入口 + 薄流程”：复用第二阶段 ResumeAgentNode（同一套 ReAct 内核、
 *    同一份 ResumeProfile 契约与证据回溯校验），不复制 Prompt/产物契约；
 * 2. 任务身份：父计划 runtimeMode=WORKFLOW_RESUME，planId 作为稳定标识返回；
 *    多份简历无法确定时置 NEED_CLARIFICATION 并列出名称，选择后按 planId 继续；
 * 3. 输出：简历事实（带原文依据）+ 已有优势 + 信息缺口 + 建议，事实与建议分开；
 * 4. 事务：会话/消息由 chatter 短事务处理，本入口 NOT_SUPPORTED，模型执行在事务外。
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
    private final JobResumeService jobResumeService;
    private final AgentArtifactService agentArtifactService;
    private final AgentPlanParameterExtractor parameterExtractor;
    private final TaskBudgetRegistry taskBudgetRegistry;
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

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public Result execute(Long userId, Long conversationId, Long planId, String traceId,
                          String message, TaskProgressPublisher publisher) {
        AgentPlan plan;
        if (planId != null) {
            plan = agentTaskService.loadOwned(userId, planId);
            if (plan == null) {
                return new Result(true, "任务不存在或不属于当前用户，已拒绝。", "REJECTED", planId, null);
            }
            if (!RUNTIME_RESUME.equals(plan.getRuntimeMode())) {
                return new Result(false, null, "NOT_HANDLED", planId, null);
            }
        } else {
            String snapshot = toJson(parameterExtractor.extract(message));
            plan = agentTaskService.createParentPlan(userId, conversationId, traceId,
                    "RESUME_ANALYZE", message, RUNTIME_RESUME, snapshot);
        }

        // 简历定位（显式 > 名称 > 默认 > 唯一；多份无法确定 → 暂停）
        Map<String, Object> explicit = parameterExtractor.extract(message);
        List<JobResume> resumes = jobResumeService.listUserResumes(userId);
        JobResume resume = pickResume(userId, explicit, resumes);
        if (resume == null) {
            String question = resumes.isEmpty()
                    ? "你的账号下还没有可用简历，请先上传一份简历。"
                    : "你有多份简历，请告诉我要分析哪一份：" + String.join("、",
                    resumes.stream().map(JobResume::getResumeName).toList()) + "。";
            agentTaskService.updateStatus(plan.getId(), AgentPlanStatus.NEED_CLARIFICATION, null, question);
            publisher.publish(new com.job.bootstrap.agent.progress.AgentProgressEvent(
                    com.job.bootstrap.agent.progress.AgentProgressEvent.WAITING_INPUT,
                    plan.getId(), null, "RESUME_AGENT", "RESUME", 0, "WAITING_INPUT", question,
                    System.currentTimeMillis()));
            return new Result(true, question, "NEED_INPUT", plan.getId(), AgentPlanStatus.NEED_CLARIFICATION.name());
        }

        TaskExecutionBudget budget = taskBudgetRegistry.getOrCreate(plan.getId());
        budget.restore(plan.getModelCallsUsed() == null ? 0 : plan.getModelCallsUsed(),
                plan.getToolCallsUsed() == null ? 0 : plan.getToolCallsUsed(), 0L);

        publisher.publish(new com.job.bootstrap.agent.progress.AgentProgressEvent(
                com.job.bootstrap.agent.progress.AgentProgressEvent.TASK_STARTED,
                plan.getId(), null, null, null, 0, "RUNNING", "开始分析简历", System.currentTimeMillis()));
        publisher.publish(new com.job.bootstrap.agent.progress.AgentProgressEvent(
                com.job.bootstrap.agent.progress.AgentProgressEvent.NODE_STARTED,
                plan.getId(), null, "RESUME_AGENT", "RESUME", 0, "RUNNING", "正在分析简历", System.currentTimeMillis()));

        ResumeAgentNode.Result result = resumeAgentNode.execute(userId, conversationId, plan.getId(), traceId,
                resume, resumeVersion(resume), text(explicit.get("keyword")), budget);
        agentTaskService.updateBudgetUsage(plan.getId(), budget.modelCallsUsed(), budget.toolCallsUsed());

        // 审查 F5：取消后不写 FAILED，也不继续发布完成事件
        if (budget.isCancelled()) {
            agentTaskService.markCancelled(plan.getId(), budget.cancelReason());
            return new Result(true, "任务已取消，已完成的结果已保留。", "CANCELLED",
                    plan.getId(), AgentPlanStatus.CANCELLED.name());
        }
        if (result.profile() == null) {
            publisher.publish(new com.job.bootstrap.agent.progress.AgentProgressEvent(
                    com.job.bootstrap.agent.progress.AgentProgressEvent.TASK_FAILED,
                    plan.getId(), result.runId(), "RESUME_AGENT", "RESUME", 0, "FAILED",
                    "简历分析失败", System.currentTimeMillis()));
            agentTaskService.updateStatus(plan.getId(), AgentPlanStatus.FAILED, result.reasonCode());
            return new Result(true, "简历分析失败（" + result.reasonCode() + "），可稍后重试。",
                    "FAILED", plan.getId(), AgentPlanStatus.FAILED.name());
        }

        agentArtifactService.save(userId, plan.getId(), result.runId(),
                AgentArtifactService.TYPE_RESUME_PROFILE, result.profile().resumeVersion(),
                toJson(result.profile()), "resumeId=" + resume.getId());

        // 审查 F3：ResumeAgent 要求补充信息时不能当成功
        if ("NEED_INPUT".equals(result.profile().outcome())) {
            String question = result.profile().question() == null
                    ? "请确认要分析的简历或补充目标方向。" : result.profile().question();
            agentTaskService.updateStatus(plan.getId(), AgentPlanStatus.NEED_CLARIFICATION, null, question);
            publisher.publish(new com.job.bootstrap.agent.progress.AgentProgressEvent(
                    com.job.bootstrap.agent.progress.AgentProgressEvent.WAITING_INPUT,
                    plan.getId(), result.runId(), "RESUME_AGENT", "RESUME", 0, "WAITING_INPUT",
                    question, System.currentTimeMillis()));
            return new Result(true, question, "NEED_INPUT", plan.getId(), AgentPlanStatus.NEED_CLARIFICATION.name());
        }

        String answer = buildAnswer(result.profile(), resume);
        publisher.publish(new com.job.bootstrap.agent.progress.AgentProgressEvent(
                com.job.bootstrap.agent.progress.AgentProgressEvent.NODE_COMPLETED,
                plan.getId(), result.runId(), "RESUME_AGENT", "RESUME", 0, "SUCCEEDED",
                "简历分析完成", System.currentTimeMillis()));
        publisher.publish(new com.job.bootstrap.agent.progress.AgentProgressEvent(
                com.job.bootstrap.agent.progress.AgentProgressEvent.TASK_COMPLETED,
                plan.getId(), null, null, null, 0, "SUCCEEDED", "任务完成", System.currentTimeMillis()));

        agentTaskService.updateStatus(plan.getId(), AgentPlanStatus.COMPLETED, null);
        return new Result(true, answer, "SUCCEEDED", plan.getId(), AgentPlanStatus.COMPLETED.name());
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

    private JobResume pickResume(Long userId, Map<String, Object> explicit, List<JobResume> resumes) {
        Long resumeId = longOf(explicit.get("resumeId"));
        if (resumeId != null) {
            return jobResumeService.getUserResumeRequired(userId, resumeId);
        }
        String resumeName = text(explicit.get("resumeName"));
        if (StringUtils.hasText(resumeName)) {
            return resumes.stream()
                    .filter(r -> r.getResumeName() != null && r.getResumeName().contains(resumeName))
                    .findFirst().orElse(null);
        }
        List<JobResume> defaults = resumes.stream()
                .filter(r -> r.getIsDefault() != null && r.getIsDefault() == 1).toList();
        if (defaults.size() == 1) {
            return defaults.get(0);
        }
        return resumes.size() == 1 ? resumes.get(0) : null;
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
