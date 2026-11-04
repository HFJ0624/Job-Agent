package com.job.bootstrap.agent.orchestration.seek;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.job.bootstrap.agent.runtime.artifact.AgentArtifactService;
import com.job.bootstrap.agent.runtime.progress.AgentProgressEvent;
import com.job.bootstrap.agent.runtime.progress.TaskProgressPublisher;
import com.job.bootstrap.agent.runtime.budget.TaskExecutionBudget;
import com.job.bootstrap.agent.domain.match.MatchAgentNode;
import com.job.bootstrap.agent.domain.match.MatchReport;
import com.job.bootstrap.agent.orchestration.plan.AgentPlanParameterExtractor;
import com.job.bootstrap.agent.runtime.react.AgentResult;
import com.job.bootstrap.agent.runtime.react.AgentRunExecutionHooks;
import com.job.bootstrap.agent.domain.resume.ResumeAgentNode;
import com.job.bootstrap.agent.domain.resume.ResumeProfile;
import com.job.bootstrap.agent.domain.job.JobCandidates;
import com.job.bootstrap.agent.domain.job.JobSearchWorkflow;
import com.job.bootstrap.agent.domain.job.SearchConstraints;
import com.job.bootstrap.service.JobResumeService;
import com.job.bootstrap.service.UserJobPreferenceService;
import com.job.common.entity.agent.AgentArtifact;
import com.job.common.entity.resume.JobResume;
import com.job.common.vo.preference.UserJobPreferenceVO;
import com.job.exception.BizException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * 功能: 求职复合任务 Workflow（第二阶段主线）。
 *
 * 固定骨架（文档规定）:
 * PREPARE_CONTEXT → ResumeAgent / JobSearchAgent（条件独立时并行）→ JOIN_RESULTS
 * → MatchAgent → VALIDATE_REPORT → RESPOND。
 *
 * 关键边界:
 * 1. 对话/计划/run 的创建与聊天消息不在本类（由入口负责），节点只拿父任务与授权输入；
 * 2. 子节点通过产物传递事实（ResumeProfile / JobCandidates / MatchReport），互不修改状态；
 * 3. 搜索为空直接返回真实结果，不调用 MatchAgent 编造报告；
 * 4. 最终答复只用**可信产物**重建（岗位字段来自候选、匹配点/缺口来自报告），不追加模型自由文本。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JobSeekWorkflow {

    /** 最终推荐上限（Top-K）。 */
    public static final int TOP_K = 5;
    /** 送入匹配的候选上限（有界，避免上下文爆炸）。 */
    private static final int MATCH_CANDIDATE_LIMIT = 8;
    /** 简历节点要求补充信息（不是失败，父任务进入 WAITING_INPUT）。 */
    private static final String RESUME_NEED_INPUT = "RESUME_NEED_INPUT";

    private final ResumeAgentNode resumeAgentNode;
    private final MatchAgentNode matchAgentNode;
    private final JobSearchWorkflow jobSearchWorkflow;
    private final JobResumeService jobResumeService;
    private final com.job.bootstrap.agent.domain.resume.ResumeSelectionService resumeSelectionService;
    private final UserJobPreferenceService userJobPreferenceService;
    private final AgentArtifactService agentArtifactService;
    private final AgentPlanParameterExtractor parameterExtractor;
    private final ObjectMapper objectMapper;

    @Qualifier(JobSeekExecutorConfig.EXECUTOR_BEAN)
    private final ThreadPoolTaskExecutor nodeExecutor;

    /**
     * 输入。
     */
    public record JobSeekInput(Long userId, Long conversationId, Long planId, String traceId,
                               String message, String constraintsJson, Long previousResumeId) {

        /** 兼容旧调用（无历史简历）。 */
        public JobSeekInput(Long userId, Long conversationId, Long planId, String traceId,
                            String message, String constraintsJson) {
            this(userId, conversationId, planId, traceId, message, constraintsJson, null);
        }
    }

    /**
     * 输出。
     *
     * @param status       SUCCEEDED / NEED_INPUT / FAILED / EMPTY
     * @param answer       对外答复（可信字段重建）
     * @param planStatus   AgentPlan 状态名
     * @param profileArtifactId 画像产物
     * @param candidatesArtifactId 候选产物
     * @param reportArtifactId 报告产物
     * @param resumeId     本次使用的简历
     */
    public record JobSeekOutcome(String status, String answer, String planStatus,
                                 Long profileArtifactId, Long candidatesArtifactId,
                                 Long reportArtifactId, Long resumeId,
                                 List<String> missingFields, String question,
                                 SearchConstraints constraints) {
    }

    public JobSeekOutcome execute(JobSeekInput input) {
        return execute(input, TaskProgressPublisher.NOOP, null);
    }

    /**
     * 执行复合任务（第三阶段：进度事件 + 父任务共享预算）。
     */
    public JobSeekOutcome execute(JobSeekInput input, TaskProgressPublisher publisher,
                                  TaskExecutionBudget budget) {
        TaskProgressPublisher progress = publisher == null ? TaskProgressPublisher.NOOP : publisher;
        publish(progress, AgentProgressEvent.TASK_STARTED, input.planId(), null, null, null,
                "RUNNING", "开始处理求职任务");

        // ---------- PREPARE_CONTEXT：条件与简历 ----------
        /*
         * 审查修复（问题二）：先合并本轮有效搜索条件（历史上下文 + 本轮显式），再解析简历。
         * 这样 NEED_INPUT / 取消等提前返回也能带上已确定的条件，
         * 不会让 Entry 把父任务里原来的城市/方向覆盖成空。
         */
        SearchConstraints constraints = mergeConstraints(input, parameterExtractor.extract(input.message()));

        ResumeResolution resumeResolution = resolveResume(input);
        if (resumeResolution.question != null) {
            publish(progress, AgentProgressEvent.WAITING_INPUT, input.planId(), null, "PREPARE_CONTEXT", null,
                    "WAITING_INPUT", resumeResolution.question);
            // 尚未选定简历：resumeId 允许为空，但条件必须带回；Entry 会与已有上下文合并
            Long previousResumeId = input.previousResumeId() != null
                    ? input.previousResumeId() : contextResumeId(input.constraintsJson());
            return new JobSeekOutcome("NEED_INPUT", resumeResolution.question, "NEED_CLARIFICATION",
                    null, null, null, previousResumeId,
                    resumeResolution.missingFields, resumeResolution.question, constraints);
        }
        JobResume resume = resumeResolution.resume;
        ensureParsed(resume);
        String resumeVersion = resumeVersion(resume);
        String constraintVersion = version(constraints, resume.getId());

        // ---------- 画像 ∥ 搜索（条件独立才并行） ----------
        boolean parallel = resumeResolution.explicitKeyword && resumeResolution.explicitCity;
        ResumeProfile profile = null;
        List<JobCandidates.JobCandidateItem> candidates = List.of();
        Long profileArtifactId = null;
        Long candidatesArtifactId = null;
        try {
            /*
             * 审查修复（问题三）：每个节点只要产出有效结果就**立即保存自己的产物**，
             * 不依赖兄弟节点是否成功；同版本保存是幂等的，JOIN 阶段不再重复插入。
             */
            ProfileArtifact reusableProfile = loadProfileArtifact(
                    input, profileVersion(resume, resumeResolution.targetRoleHint));
            CandidatesArtifact reusableCandidates = loadCandidatesArtifact(input, constraintVersion);
            if (reusableProfile != null) {
                profileArtifactId = reusableProfile.artifactId();
            }
            if (reusableCandidates != null) {
                candidatesArtifactId = reusableCandidates.artifactId();
            }

            if (parallel) {
                publish(progress, AgentProgressEvent.NODE_STARTED, input.planId(), null, "RESUME_AGENT", "RESUME",
                        "RUNNING", reusableProfile != null ? "复用已完成的简历分析" : "正在分析简历");
                Future<NodeOutput> resumeFuture = nodeExecutor.submit(
                        () -> reusableProfile != null
                                ? new NodeOutput(null, reusableProfile.profile(), List.of())
                                : resumeNode(input, resume, resumeVersion, resumeResolution.targetRoleHint, budget));
                publish(progress, AgentProgressEvent.NODE_STARTED, input.planId(), null, "JOB_SEARCH_AGENT", "JOB_SEARCH",
                        "RUNNING", reusableCandidates != null ? "复用已完成的岗位搜索结果" : "正在搜索岗位");
                Future<NodeOutput> searchFuture = nodeExecutor.submit(
                        () -> reusableCandidates != null
                                ? new NodeOutput(null, null, reusableCandidates.candidates())
                                : searchNode(input, constraints, budget));

                NodeOutput resumeOut = await(resumeFuture, budget);
                NodeOutput searchOut = await(searchFuture, budget);

                // 成功节点先各自落库（画像成功/搜索失败时画像产物仍然保留）
                if (resumeOut.ok() && reusableProfile == null) {
                    profile = (ResumeProfile) resumeOut.payload();
                    profileArtifactId = saveArtifact(input, AgentArtifactService.TYPE_RESUME_PROFILE,
                            profileVersion(resume, resumeResolution.targetRoleHint), profile,
                            "resumeId=" + resume.getId());
                } else if (resumeOut.ok()) {
                    profile = reusableProfile.profile();
                }
                if (searchOut.ok() && reusableCandidates == null) {
                    candidates = candidatesOf(searchOut);
                    candidatesArtifactId = saveArtifact(input, AgentArtifactService.TYPE_JOB_CANDIDATES,
                            constraintVersion, candidates, "resumeId=" + resume.getId());
                } else if (searchOut.ok()) {
                    candidates = reusableCandidates.candidates();
                }

                if (!resumeOut.ok()) {
                    if (RESUME_NEED_INPUT.equals(resumeOut.reasonCode())) {
                        return new JobSeekOutcome("NEED_INPUT",
                                "我需要你补充一点信息才能继续：请确认要使用的简历或更明确的目标方向。",
                                "NEED_CLARIFICATION", profileArtifactId, candidatesArtifactId, null, resume.getId(),
                                List.of("resume"), "请确认要使用的简历或更明确的目标方向。", constraints);
                    }
                    return failed(input, resumeOut, "RESUME_NODE_FAILED", constraints,
                            profileArtifactId, candidatesArtifactId, resume.getId());
                }
                if (!searchOut.ok()) {
                    return failed(input, searchOut, "SEARCH_NODE_FAILED", constraints,
                            profileArtifactId, candidatesArtifactId, resume.getId());
                }
                // 审查 F6：并行分支必须补齐两个节点的完成事件，否则页面永远显示“进行中”
                publish(progress, AgentProgressEvent.NODE_COMPLETED, input.planId(), null, "RESUME_AGENT", "RESUME",
                        "SUCCEEDED", reusableProfile != null ? "复用已完成的简历分析"
                                : "简历分析完成（" + profile.skills().size() + " 项技能）");
                publish(progress, AgentProgressEvent.NODE_COMPLETED, input.planId(), null, "JOB_SEARCH_AGENT", "JOB_SEARCH",
                        "SUCCEEDED", reusableCandidates != null ? "复用已完成的岗位搜索结果"
                                : "岗位搜索完成（" + candidates.size() + " 个候选）");
            } else {
                // 取消检查：已取消不再启动后续节点（第三阶段）
                if (budget != null && !budget.canContinue()) {
                    return new JobSeekOutcome("FAILED", "任务已取消或超时，已完成结果已保留。", "FAILED",
                            profileArtifactId, candidatesArtifactId, null, resume.getId(), List.of(), null, constraints);
                }
                publish(progress, AgentProgressEvent.NODE_STARTED, input.planId(), null, "RESUME_AGENT", "RESUME",
                        "RUNNING", reusableProfile != null ? "复用已完成的简历分析" : "正在分析简历");
                NodeOutput resumeOut = reusableProfile != null
                        ? new NodeOutput(null, reusableProfile.profile(), List.of())
                        : resumeNode(input, resume, resumeVersion, resumeResolution.targetRoleHint, budget);
                if (!resumeOut.ok()) {
                    if (RESUME_NEED_INPUT.equals(resumeOut.reasonCode())) {
                        return new JobSeekOutcome("NEED_INPUT",
                                "我需要你补充一点信息才能继续：请确认要使用的简历或更明确的目标方向。",
                                "NEED_CLARIFICATION", candidatesArtifactId, null, null, resume.getId(),
                                List.of("resume"), "请确认要使用的简历或更明确的目标方向。", constraints);
                    }
                    return failed(input, resumeOut, "RESUME_NODE_FAILED", constraints,
                            profileArtifactId, candidatesArtifactId, resume.getId());
                }
                profile = (ResumeProfile) resumeOut.payload();
                if (reusableProfile == null) {
                    // 画像成功立即保存，后续搜索失败也能复用
                    profileArtifactId = saveArtifact(input, AgentArtifactService.TYPE_RESUME_PROFILE,
                            profileVersion(resume, resumeResolution.targetRoleHint), profile,
                            "resumeId=" + resume.getId());
                }
                publish(progress, AgentProgressEvent.NODE_COMPLETED, input.planId(), null, "RESUME_AGENT", "RESUME",
                        "SUCCEEDED", reusableProfile != null ? "复用已完成的简历分析"
                                : "简历分析完成（" + profile.skills().size() + " 项技能）");

                // 无明确关键词时，用画像推断的方向作为召回词（目标方向仍受约束）
                SearchConstraints searchConstraints = constraints;
                if (!StringUtils.hasText(constraints.keyword())
                        && StringUtils.hasText(profile.targetRole())) {
                    searchConstraints = SearchConstraints.merge(
                            Map.of("keyword", profile.targetRole()), constraints, null);
                }
                publish(progress, AgentProgressEvent.NODE_STARTED, input.planId(), null, "JOB_SEARCH_AGENT", "JOB_SEARCH",
                        "RUNNING", reusableCandidates != null ? "复用已完成的岗位搜索结果" : "正在搜索岗位");
                NodeOutput searchOut = reusableCandidates != null
                        ? new NodeOutput(null, null, reusableCandidates.candidates())
                        : searchNode(input, searchConstraints, budget);
                if (!searchOut.ok()) {
                    return failed(input, searchOut, "SEARCH_NODE_FAILED", constraints,
                            profileArtifactId, candidatesArtifactId, resume.getId());
                }
                candidates = candidatesOf(searchOut);
                if (reusableCandidates == null) {
                    // 候选成功立即保存，后续匹配失败也能复用
                    candidatesArtifactId = saveArtifact(input, AgentArtifactService.TYPE_JOB_CANDIDATES,
                            constraintVersion, candidates, "resumeId=" + resume.getId());
                }
                publish(progress, AgentProgressEvent.NODE_COMPLETED, input.planId(), null, "JOB_SEARCH_AGENT", "JOB_SEARCH",
                        "SUCCEEDED", reusableCandidates != null ? "复用已完成的岗位搜索结果"
                                : "岗位搜索完成（" + candidates.size() + " 个候选）");
            }
        } catch (Exception exception) {
            log.warn("复合任务节点执行异常，error={}", exception.getMessage());
            publish(progress, AgentProgressEvent.TASK_FAILED, input.planId(), null, null, null,
                    "FAILED", "任务执行失败");
            return new JobSeekOutcome("FAILED", "任务执行失败：" + exception.getMessage(), "FAILED",
                    profileArtifactId, candidatesArtifactId, null, resume.getId(), List.of(), null, constraints);
        }

        // ---------- JOIN_RESULTS：搜索为空直接返回，不编造匹配 ----------
        if (candidates.isEmpty()) {
            publish(progress, AgentProgressEvent.TASK_COMPLETED, input.planId(), null, null, null,
                    "EMPTY", "搜索完成，无符合条件的岗位");
            return new JobSeekOutcome("EMPTY",
                    "我按条件查询了岗位，但当前没有符合条件的结果。可以放宽城市或关键词，要我调整后再查一次吗？",
                    "COMPLETED", profileArtifactId, candidatesArtifactId, null, resume.getId(),
                    List.of(), null, constraints);
        }

        // ---------- MatchAgent（有界候选） ----------
        List<JobCandidates.JobCandidateItem> matchCandidates = candidates.stream()
                .limit(MATCH_CANDIDATE_LIMIT).toList();

        /*
         * P0 修复：报告版本 = 条件版本 + 画像/候选产物 ID。
         * 同版本已存在报告时直接复用，避免"匹配失败重试"把匹配节点再跑一遍；
         * 简历或条件变化 → 版本变化 → 不复用旧报告。
         */
        String reportVersion = reportVersion(constraintVersion, profileArtifactId, candidatesArtifactId);
        MatchReport reusableReport = loadReportArtifact(input, reportVersion);
        if (reusableReport != null) {
            publish(progress, AgentProgressEvent.NODE_COMPLETED, input.planId(), null, "MATCH_AGENT", "MATCH",
                    "SUCCEEDED", "复用已完成的匹配报告");
            publish(progress, AgentProgressEvent.TASK_COMPLETED, input.planId(), null, null, null,
                    "SUCCEEDED", "任务完成");
            return new JobSeekOutcome("SUCCEEDED", buildAnswer(matchCandidates, reusableReport), "COMPLETED",
                    profileArtifactId, candidatesArtifactId, null, resume.getId(),
                    List.of(), null, constraints);
        }

        publish(progress, AgentProgressEvent.NODE_STARTED, input.planId(), null, "MATCH_AGENT", "MATCH",
                "RUNNING", "正在比较岗位与简历");
        MatchAgentNode.Result matchResult = matchAgentNode.execute(
                input.userId(), input.conversationId(), input.planId(), input.traceId(),
                resume, profile, matchCandidates, constraints, TOP_K, constraintVersion,
                profileArtifactId, candidatesArtifactId, budget);
        if (matchResult.report() == null) {
            publish(progress, AgentProgressEvent.TASK_FAILED, input.planId(), matchResult.runId(), "MATCH_AGENT", "MATCH",
                    "FAILED", "匹配分析失败，可重试该节点");
            return new JobSeekOutcome("FAILED",
                    "匹配分析失败（" + matchResult.reasonCode() + "），岗位结果已保留，可稍后重试匹配。",
                    "FAILED", profileArtifactId, candidatesArtifactId, null, resume.getId(),
                    List.of(), null, constraints);
        }
        Long reportArtifactId = saveArtifact(input, AgentArtifactService.TYPE_MATCH_REPORT,
                reportVersion, matchResult.report(), "profile=" + profileArtifactId
                        + ",candidates=" + candidatesArtifactId);

        // ---------- VALIDATE_REPORT + RESPOND（可信字段重建） ----------
        String answer = buildAnswer(matchCandidates, matchResult.report());
        publish(progress, AgentProgressEvent.NODE_COMPLETED, input.planId(), matchResult.runId(), "MATCH_AGENT", "MATCH",
                "SUCCEEDED", "匹配完成（" + matchResult.report().rankedItems().size() + " 个推荐）");
        publish(progress, AgentProgressEvent.TASK_COMPLETED, input.planId(), null, null, null,
                "SUCCEEDED", "任务完成");
        return new JobSeekOutcome("SUCCEEDED", answer, "COMPLETED",
                profileArtifactId, candidatesArtifactId, reportArtifactId, resume.getId(),
                List.of(), null, constraints);
    }

    // ---------- 节点执行 ----------

    /** 复用的画像产物：artifactId 供报告版本使用。 */
    private record ProfileArtifact(Long artifactId, ResumeProfile profile) {
    }

    /** 复用的候选产物：artifactId 供报告版本使用。 */
    private record CandidatesArtifact(Long artifactId, List<JobCandidates.JobCandidateItem> candidates) {
    }

    private record NodeOutput(String reasonCode, Object payload, List<JobCandidates.JobCandidateItem> candidates) {
        boolean ok() {
            return reasonCode == null;
        }
    }

    private NodeOutput resumeNode(JobSeekInput input, JobResume resume, String resumeVersion,
                                  String targetRoleHint, TaskExecutionBudget budget) {
        ResumeAgentNode.Result result = resumeAgentNode.execute(
                input.userId(), input.conversationId(), input.planId(), input.traceId(),
                resume, resumeVersion, targetRoleHint, budget);
        if (result.profile() == null) {
            return new NodeOutput(result.reasonCode(), null, List.of());
        }
        // 审查 F3：Agent 提出补充问题时不能当作成功继续搜索
        if ("NEED_INPUT".equals(result.profile().outcome())) {
            return new NodeOutput(RESUME_NEED_INPUT, null, List.of());
        }
        return new NodeOutput(null, result.profile(), List.of());
    }

    private NodeOutput searchNode(JobSeekInput input, SearchConstraints constraints,
                                  TaskExecutionBudget budget) {
        /*
         * 审查 F1：搜索节点同样要受父任务预算/取消约束。
         * 并行线程不会继承主线程 ThreadLocal，所以在这里显式设置执行钩子（含 budget）。
         */
        AgentRunExecutionHooks.set(null, null, budget);
        try {
            JobSearchWorkflow.WorkflowOutcome outcome = jobSearchWorkflow.execute(
                    new JobSearchWorkflow.WorkflowInput(
                            input.userId(), input.traceId(), input.message(), constraints,
                            null, 0, 0, System.currentTimeMillis(),
                            input.conversationId(), "JOB_SEEK_FLOW", List.of()));
            if (outcome.outcome() == AgentResult.Outcome.FAILED) {
                return new NodeOutput(outcome.reasonCode(), null, List.of());
            }
            return new NodeOutput(null, null, outcome.candidates());
        } finally {
            AgentRunExecutionHooks.clear();
        }
    }

    private List<JobCandidates.JobCandidateItem> candidatesOf(NodeOutput output) {
        return output.candidates() == null ? List.of() : output.candidates();
    }

    private JobSeekOutcome failed(JobSeekInput input, NodeOutput output, String fallbackReason,
                                  SearchConstraints constraints, Long profileArtifactId,
                                  Long candidatesArtifactId, Long resumeId) {
        String reason = output.reasonCode() == null ? fallbackReason : output.reasonCode();
        // 失败时把已成功的产物 ID 与简历带回去，重试只跑失败节点
        return new JobSeekOutcome("FAILED", "任务失败：" + reason + "。已完成的部分已保存，可重试失败节点。",
                "FAILED", profileArtifactId, candidatesArtifactId, null, resumeId, List.of(), null, constraints);
    }

    private NodeOutput await(Future<NodeOutput> future, TaskExecutionBudget budget) throws Exception {
        long timeout = 180_000L;
        if (budget != null) {
            timeout = Math.min(timeout, Math.max(1_000L, budget.remainingMillis()));
        }
        return future.get(timeout, TimeUnit.MILLISECONDS);
    }

    // ---------- 简历解析 ----------

    private record ResumeResolution(JobResume resume, String question, List<String> missingFields,
                                    Map<String, Object> explicitParams, String targetRoleHint,
                                    boolean explicitKeyword, boolean explicitCity) {
    }

    private ResumeResolution resolveResume(JobSeekInput input) {
        Map<String, Object> explicit = parameterExtractor.extract(input.message());
        String targetRoleHint = text(explicit.get("keyword"));

        // 简历定位统一走 ResumeSelectionService（P1：复合/独立分析共用一套规则）
        Long previousResumeId = input.previousResumeId() != null
                ? input.previousResumeId() : contextResumeId(input.constraintsJson());
        com.job.bootstrap.agent.domain.resume.ResumeSelectionService.Selection selection =
                resumeSelectionService.select(input.userId(), explicit, previousResumeId, "做匹配分析");
        if (selection.needsInput()) {
            return new ResumeResolution(null, selection.question(), selection.missingFields(),
                    explicit, targetRoleHint, false, false);
        }
        return new ResumeResolution(selection.resume(), null, List.of(), explicit, targetRoleHint,
                StringUtils.hasText(targetRoleHint), StringUtils.hasText(text(explicit.get("city"))));
    }

    // 准备阶段允许写：未解析的简历在这里触发解析（不把带写入的动作伪装成只读）
    private void ensureParsed(JobResume resume) {
        if (!StringUtils.hasText(resume.getRawText())) {
            JobResume parsed = jobResumeService.parseResumeText(resume.getUserId(), resume.getId());
            if (parsed != null && StringUtils.hasText(parsed.getRawText())) {
                resume.setRawText(parsed.getRawText());
            } else {
                throw new BizException("简历没有解析文本，无法生成画像");
            }
        }
    }

    private SearchConstraints mergeConstraints(JobSeekInput input, Map<String, Object> explicit) {
        SearchConstraints previous = null;
        Map<String, Object> wrapper = readJsonMap(input.constraintsJson());
        if (wrapper != null && wrapper.get("constraints") instanceof Map<?, ?> nested) {
            // 任务上下文包装格式（审查 F3）：{constraints:{...}, resumeId:n}
            previous = SearchConstraints.merge(castMap(nested), null);
            wrapper = null;
        }
        if (previous == null && StringUtils.hasText(input.constraintsJson())) {
            /*
             * 审查 F3：历史快照可能是初始参数 Map，也可能已是 SearchConstraints JSON，
             * 两种格式都要能读回，否则补参时条件全部丢失。
             */
            try {
                SearchConstraints parsed = objectMapper.readValue(input.constraintsJson(), SearchConstraints.class);
                if (parsed.city() != null || parsed.keyword() != null || parsed.targetRole() != null
                        || parsed.minSalary() != null) {
                    previous = parsed;
                }
            } catch (Exception ignored) {
                try {
                    Map<String, Object> raw = objectMapper.readValue(input.constraintsJson(),
                            objectMapper.getTypeFactory().constructMapType(Map.class, String.class, Object.class));
                    previous = SearchConstraints.merge(raw, null);
                } catch (Exception second) {
                    previous = null;
                }
            }
        }
        UserJobPreferenceVO preference = null;
        try {
            preference = userJobPreferenceService.getPreference(input.userId());
        } catch (Exception exception) {
            log.debug("偏好加载失败，按无偏好处理，error={}", exception.getMessage());
        }
        return SearchConstraints.merge(explicit, previous, preference);
    }

    private Map<String, Object> readJsonMap(String json) {
        if (!StringUtils.hasText(json)) {
            return null;
        }
        try {
            return objectMapper.readValue(json,
                    objectMapper.getTypeFactory().constructMapType(Map.class, String.class, Object.class));
        } catch (Exception exception) {
            return null;
        }
    }

    private Long contextResumeId(String json) {
        Map<String, Object> map = readJsonMap(json);
        if (map == null || !(map.get("resumeId") instanceof Number number)) {
            return null;
        }
        return number.longValue();
    }

    // ---------- 产物 ----------

    private Long saveArtifact(JobSeekInput input, String type, String version, Object payload, String refs) {
        try {
            AgentArtifact existing = agentArtifactService.findLatest(input.userId(), input.planId(), type, version);
            if (existing != null) {
                return existing.getId(); // 幂等：同版本不重复写
            }
            AgentArtifact artifact = agentArtifactService.save(input.userId(), input.planId(), null,
                    type, version, toJson(payload), refs);
            return artifact.getId();
        } catch (Exception exception) {
            /*
             * 审查问题三-9：产物保存失败必须显式失败，不能继续对用户声称"已完成"。
             * 由上层 catch 转成 FAILED，并用明确文案告知重试。
             */
            throw new IllegalStateException("结果保存失败（" + type + "）：" + exception.getMessage(), exception);
        }
    }

    // 复用已完成的画像产物（简历未变才复用）
    private ProfileArtifact loadProfileArtifact(JobSeekInput input, String resumeVersion) {
        try {
            AgentArtifact artifact = agentArtifactService.findLatest(
                    input.userId(), input.planId(), AgentArtifactService.TYPE_RESUME_PROFILE, resumeVersion);
            if (artifact == null || !StringUtils.hasText(artifact.getContentJson())) {
                return null;
            }
            // 复用时要带上产物 ID，报告版本依赖它
            return new ProfileArtifact(artifact.getId(),
                    objectMapper.readValue(artifact.getContentJson(), ResumeProfile.class));
        } catch (Exception exception) {
            log.warn("画像产物解析失败，按未命中处理，error={}", exception.getMessage());
            return null;
        }
    }

    // 复用已完成的候选产物（条件版本未变才复用）
    private CandidatesArtifact loadCandidatesArtifact(JobSeekInput input, String constraintVersion) {
        try {
            AgentArtifact artifact = agentArtifactService.findLatest(
                    input.userId(), input.planId(), AgentArtifactService.TYPE_JOB_CANDIDATES, constraintVersion);
            if (artifact == null || !StringUtils.hasText(artifact.getContentJson())) {
                return null;
            }
            // 统一走 codec（P1：消除内联解析与工具类两份实现）
            List<JobCandidates.JobCandidateItem> items = JobCandidateArtifactCodec.decode(
                    artifact.getContentJson(), objectMapper);
            return items.isEmpty() ? null : new CandidatesArtifact(artifact.getId(), items);
        } catch (Exception exception) {
            log.warn("候选产物解析失败，按未命中处理，error={}", exception.getMessage());
            return null;
        }
    }

    // 复用同版本的匹配报告（报告版本已包含画像/候选产物，版本一致内容才可靠）
    private MatchReport loadReportArtifact(JobSeekInput input, String reportVersion) {
        try {
            AgentArtifact artifact = agentArtifactService.findLatest(
                    input.userId(), input.planId(), AgentArtifactService.TYPE_MATCH_REPORT, reportVersion);
            if (artifact == null || !StringUtils.hasText(artifact.getContentJson())) {
                return null;
            }
            return objectMapper.readValue(artifact.getContentJson(), MatchReport.class);
        } catch (Exception exception) {
            log.warn("匹配报告解析失败，按未命中处理，error={}", exception.getMessage());
            return null;
        }
    }

    // ---------- 答复重建 ----------

    private String buildAnswer(List<JobCandidates.JobCandidateItem> candidates, MatchReport report) {
        Map<Long, Map<String, Object>> rawById = new LinkedHashMap<>();
        candidates.forEach(item -> rawById.put(item.jobId(), item.raw()));

        StringBuilder builder = new StringBuilder();
        builder.append("结合你的简历，我从 ").append(candidates.size()).append(" 个候选里挑出 ")
                .append(report.rankedItems().size()).append(" 个岗位：\n\n");

        int index = 1;
        for (MatchReport.RankedItem item : report.rankedItems()) {
            Map<String, Object> raw = rawById.get(item.jobId());
            if (raw == null) {
                continue; // 只输出可信候选
            }
            builder.append(index++).append(". ")
                    .append(text(raw.get("jobTitle"))).append("｜")
                    .append(companyOf(raw)).append("｜")
                    .append(text(raw.get("city")));
            String salary = salaryOf(raw.get("minSalary"), raw.get("maxSalary"));
            if (StringUtils.hasText(salary)) {
                builder.append("｜").append(salary);
            }
            builder.append("\n");
            if (!item.matchPoints().isEmpty()) {
                builder.append("   匹配点：").append(String.join("；", limit(item.matchPoints(), 3))).append("\n");
            }
            if (!item.gaps().isEmpty()) {
                builder.append("   待补能力（简历未见证据）：").append(String.join("；", limit(item.gaps(), 3))).append("\n");
            }
            if (!item.suggestions().isEmpty()) {
                builder.append("   建议：").append(String.join("；", limit(item.suggestions(), 2))).append("\n");
            }
        }

        if (report.completedCount() < report.totalCandidates()) {
            builder.append("\n说明：候选共 ").append(report.totalCandidates())
                    .append(" 个，本次完整比较 ").append(report.completedCount()).append(" 个");
            if (!report.uncertainties().isEmpty()) {
                builder.append("；").append(String.join("；", limit(report.uncertainties(), 2)));
            }
            builder.append("。\n");
        }
        builder.append("\n如果你愿意，我可以针对排名第一的岗位给出更细的面试准备计划。");
        return builder.toString();
    }

    private String companyOf(Map<String, Object> raw) {
        String company = text(raw.get("companyName"));
        return StringUtils.hasText(company) ? company : "公司未知";
    }

    // 元/月 → K/月
    private String salaryOf(Object minValue, Object maxValue) {
        Integer min = integerOf(minValue);
        Integer max = integerOf(maxValue);
        if (min == null && max == null) {
            return null;
        }
        String left = min == null ? "?" : String.valueOf(Math.round(min / 1000.0));
        String right = max == null ? "?" : String.valueOf(Math.round(max / 1000.0));
        return left + "-" + right + "K/月";
    }

    private List<String> limit(List<String> values, int max) {
        return values.size() > max ? values.subList(0, max) : values;
    }

    // 进度事件（不暴露内部参数/Prompt/推理）
    private void publish(TaskProgressPublisher publisher, String event, Long planId, Long runId,
                         String nodeKey, String agentType, String status, String summary) {
        if (publisher == null) {
            return;
        }
        publisher.publish(new AgentProgressEvent(event, planId, runId, nodeKey, agentType,
                0, status, summary, System.currentTimeMillis()));
    }

    // ---------- 工具方法 ----------

    /*
     * 画像版本 = 简历版本 + 用户显式目标方向（空表示未指定）。
     * 保存与复用必须用同一入参，否则永远命中不了；用户后来指定方向时版本变化，
     * 目标相关分析自然刷新（审查 F4）。
     */
    private String profileVersion(JobResume resume, String targetRoleHint) {
        return hash(resumeVersion(resume) + "|" + (targetRoleHint == null ? "" : targetRoleHint));
    }

    // 报告版本 = 条件版本 + 画像/候选产物 ID（上下游变化则旧报告失效）
    private String reportVersion(String constraintVersion, Long profileArtifactId, Long candidatesArtifactId) {
        return hash(constraintVersion + "|" + profileArtifactId + "|" + candidatesArtifactId);
    }

    private Map<String, Object> castMap(Map<?, ?> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> result.put(String.valueOf(key), value));
        return result;
    }

    private String resumeVersion(JobResume resume) {
        String seed = resume.getId() + ":" + (resume.getUpdateTime() == null ? ""
                : resume.getUpdateTime().getTime()) + ":" + hash(resume.getRawText());
        return hash(seed);
    }

    private String version(SearchConstraints constraints, Long resumeId) {
        return hash(resumeId + "|" + constraints.targetRole() + "|" + constraints.city() + "|"
                + constraints.minSalary() + "|" + constraints.educationReq() + "|" + constraints.keyword());
    }

    private String hash(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest((text == null ? "" : text).getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < 8; i++) {
                builder.append(String.format("%02x", bytes[i]));
            }
            return builder.toString();
        } catch (Exception exception) {
            return String.valueOf(text == null ? 0 : text.hashCode());
        }
    }

    private String text(Object value) {
        return value == null ? null : String.valueOf(value).trim();
    }


    private Integer integerOf(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && StringUtils.hasText(text)) {
            try {
                return Integer.parseInt(text.trim());
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
            return null;
        }
    }
}
