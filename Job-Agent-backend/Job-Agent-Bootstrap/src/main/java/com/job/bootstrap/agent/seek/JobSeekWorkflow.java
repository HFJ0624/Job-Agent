package com.job.bootstrap.agent.seek;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.job.bootstrap.agent.artifact.AgentArtifactService;
import com.job.bootstrap.agent.match.MatchAgentNode;
import com.job.bootstrap.agent.match.MatchReport;
import com.job.bootstrap.agent.plan.AgentPlanParameterExtractor;
import com.job.bootstrap.agent.react.AgentResult;
import com.job.bootstrap.agent.resume.ResumeAgentNode;
import com.job.bootstrap.agent.resume.ResumeProfile;
import com.job.bootstrap.agent.search.JobCandidates;
import com.job.bootstrap.agent.search.JobSearchWorkflow;
import com.job.bootstrap.agent.search.SearchConstraints;
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

    private final ResumeAgentNode resumeAgentNode;
    private final MatchAgentNode matchAgentNode;
    private final JobSearchWorkflow jobSearchWorkflow;
    private final JobResumeService jobResumeService;
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
                               String message, String constraintsJson) {
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
                                 List<String> missingFields, String question) {
    }

    public JobSeekOutcome execute(JobSeekInput input) {
        // ---------- PREPARE_CONTEXT：简历与条件 ----------
        ResumeResolution resumeResolution = resolveResume(input);
        if (resumeResolution.question != null) {
            return new JobSeekOutcome("NEED_INPUT", resumeResolution.question, "NEED_CLARIFICATION",
                    null, null, null, null, resumeResolution.missingFields, resumeResolution.question);
        }
        JobResume resume = resumeResolution.resume;
        ensureParsed(resume);
        String resumeVersion = resumeVersion(resume);

        SearchConstraints constraints = mergeConstraints(input, resumeResolution.explicitParams);
        String constraintVersion = version(constraints, resume.getId());

        // ---------- 画像 ∥ 搜索（条件独立才并行） ----------
        boolean parallel = resumeResolution.explicitKeyword && resumeResolution.explicitCity;
        ResumeProfile profile;
        List<JobCandidates.JobCandidateItem> candidates;
        try {
            // 产物复用（第二阶段）：同版本已完成的节点不重跑——画像看简历版本，候选看条件版本
            ResumeProfile reusableProfile = loadProfileArtifact(input, resumeVersion);
            List<JobCandidates.JobCandidateItem> reusableCandidates =
                    loadCandidatesArtifact(input, constraintVersion);

            if (parallel) {
                Future<NodeOutput> resumeFuture = nodeExecutor.submit(
                        () -> reusableProfile != null
                                ? new NodeOutput(null, reusableProfile, List.of())
                                : resumeNode(input, resume, resumeVersion, resumeResolution.targetRoleHint));
                Future<NodeOutput> searchFuture = nodeExecutor.submit(
                        () -> reusableCandidates != null
                                ? new NodeOutput(null, null, reusableCandidates)
                                : searchNode(input, constraints));
                NodeOutput resumeOut = await(resumeFuture);
                NodeOutput searchOut = await(searchFuture);
                if (!resumeOut.ok()) {
                    return failed(input, resumeOut, "RESUME_NODE_FAILED");
                }
                if (!searchOut.ok()) {
                    return failed(input, searchOut, "SEARCH_NODE_FAILED");
                }
                profile = (ResumeProfile) resumeOut.payload();
                candidates = candidatesOf(searchOut);
            } else {
                NodeOutput resumeOut = reusableProfile != null
                        ? new NodeOutput(null, reusableProfile, List.of())
                        : resumeNode(input, resume, resumeVersion, resumeResolution.targetRoleHint);
                if (!resumeOut.ok()) {
                    return failed(input, resumeOut, "RESUME_NODE_FAILED");
                }
                profile = (ResumeProfile) resumeOut.payload();
                // 无明确关键词时，用画像推断的方向作为召回词（目标方向仍受约束）
                SearchConstraints searchConstraints = constraints;
                if (!StringUtils.hasText(constraints.keyword())
                        && StringUtils.hasText(profile.targetRole())) {
                    searchConstraints = SearchConstraints.merge(
                            Map.of("keyword", profile.targetRole()), constraints, null);
                }
                NodeOutput searchOut = reusableCandidates != null
                        ? new NodeOutput(null, null, reusableCandidates)
                        : searchNode(input, searchConstraints);
                if (!searchOut.ok()) {
                    return failed(input, searchOut, "SEARCH_NODE_FAILED");
                }
                candidates = candidatesOf(searchOut);
            }
        } catch (Exception exception) {
            log.warn("复合任务节点执行异常，error={}", exception.getMessage());
            return new JobSeekOutcome("FAILED", "任务执行失败：" + exception.getMessage(), "FAILED",
                    null, null, null, resume.getId(), List.of(), null);
        }

        Long profileArtifactId = saveArtifact(input, AgentArtifactService.TYPE_RESUME_PROFILE,
                resumeVersion, profile, "resumeId=" + resume.getId());
        Long candidatesArtifactId = saveArtifact(input, AgentArtifactService.TYPE_JOB_CANDIDATES,
                constraintVersion, candidates, "resumeId=" + resume.getId());

        // ---------- JOIN_RESULTS：搜索为空直接返回，不编造匹配 ----------
        if (candidates.isEmpty()) {
            return new JobSeekOutcome("EMPTY",
                    "我按条件查询了岗位，但当前没有符合条件的结果。可以放宽城市或关键词，要我调整后再查一次吗？",
                    "COMPLETED", profileArtifactId, candidatesArtifactId, null, resume.getId(),
                    List.of(), null);
        }

        // ---------- MatchAgent（有界候选） ----------
        List<JobCandidates.JobCandidateItem> matchCandidates = candidates.stream()
                .limit(MATCH_CANDIDATE_LIMIT).toList();
        MatchAgentNode.Result matchResult = matchAgentNode.execute(
                input.userId(), input.conversationId(), input.planId(), input.traceId(),
                resume, profile, matchCandidates, constraints, TOP_K, constraintVersion,
                profileArtifactId, candidatesArtifactId);
        if (matchResult.report() == null) {
            return new JobSeekOutcome("FAILED",
                    "匹配分析失败（" + matchResult.reasonCode() + "），岗位结果已保留，可稍后重试匹配。",
                    "FAILED", profileArtifactId, candidatesArtifactId, null, resume.getId(),
                    List.of(), null);
        }
        Long reportArtifactId = saveArtifact(input, AgentArtifactService.TYPE_MATCH_REPORT,
                constraintVersion, matchResult.report(), "profile=" + profileArtifactId
                        + ",candidates=" + candidatesArtifactId);

        // ---------- VALIDATE_REPORT + RESPOND（可信字段重建） ----------
        String answer = buildAnswer(matchCandidates, matchResult.report());
        return new JobSeekOutcome("SUCCEEDED", answer, "COMPLETED",
                profileArtifactId, candidatesArtifactId, reportArtifactId, resume.getId(),
                List.of(), null);
    }

    // ---------- 节点执行 ----------

    private record NodeOutput(String reasonCode, Object payload, List<JobCandidates.JobCandidateItem> candidates) {
        boolean ok() {
            return reasonCode == null;
        }
    }

    private NodeOutput resumeNode(JobSeekInput input, JobResume resume, String resumeVersion,
                                  String targetRoleHint) {
        ResumeAgentNode.Result result = resumeAgentNode.execute(
                input.userId(), input.conversationId(), input.planId(), input.traceId(),
                resume, resumeVersion, targetRoleHint);
        if (result.profile() == null) {
            return new NodeOutput(result.reasonCode(), null, List.of());
        }
        return new NodeOutput(null, result.profile(), List.of());
    }

    private NodeOutput searchNode(JobSeekInput input, SearchConstraints constraints) {
        JobSearchWorkflow.WorkflowOutcome outcome = jobSearchWorkflow.execute(
                new JobSearchWorkflow.WorkflowInput(
                        input.userId(), input.traceId(), input.message(), constraints,
                        null, 0, 0, System.currentTimeMillis(),
                        input.conversationId(), "JOB_SEEK_FLOW", List.of()));
        if (outcome.outcome() == AgentResult.Outcome.FAILED) {
            return new NodeOutput(outcome.reasonCode(), null, List.of());
        }
        return new NodeOutput(null, null, outcome.candidates());
    }

    private List<JobCandidates.JobCandidateItem> candidatesOf(NodeOutput output) {
        return output.candidates() == null ? List.of() : output.candidates();
    }

    private JobSeekOutcome failed(JobSeekInput input, NodeOutput output, String fallbackReason) {
        String reason = output.reasonCode() == null ? fallbackReason : output.reasonCode();
        return new JobSeekOutcome("FAILED", "任务失败：" + reason + "。已完成的部分已保存，可重试失败节点。",
                "FAILED", null, null, null, null, List.of(), null);
    }

    private NodeOutput await(Future<NodeOutput> future) throws Exception {
        return future.get(180, TimeUnit.SECONDS);
    }

    // ---------- 简历解析 ----------

    private record ResumeResolution(JobResume resume, String question, List<String> missingFields,
                                    Map<String, Object> explicitParams, String targetRoleHint,
                                    boolean explicitKeyword, boolean explicitCity) {
    }

    private ResumeResolution resolveResume(JobSeekInput input) {
        Map<String, Object> explicit = parameterExtractor.extract(input.message());
        String targetRoleHint = text(explicit.get("keyword"));

        Long resumeId = longOf(explicit.get("resumeId"));
        if (resumeId != null) {
            JobResume resume = jobResumeService.getUserResumeRequired(input.userId(), resumeId);
            return new ResumeResolution(resume, null, List.of(), explicit, targetRoleHint,
                    StringUtils.hasText(targetRoleHint), StringUtils.hasText(text(explicit.get("city"))));
        }

        String resumeName = text(explicit.get("resumeName"));
        List<JobResume> resumes = jobResumeService.listUserResumes(input.userId());
        if (resumes.isEmpty()) {
            return new ResumeResolution(null,
                    "你的账号下还没有可用的简历，请先上传一份简历，我再帮你做匹配分析。",
                    List.of("resume"), explicit, targetRoleHint, false, false);
        }

        JobResume picked = null;
        if (StringUtils.hasText(resumeName)) {
            picked = resumes.stream()
                    .filter(r -> r.getResumeName() != null && r.getResumeName().contains(resumeName))
                    .findFirst().orElse(null);
        }
        if (picked == null) {
            List<JobResume> defaults = resumes.stream()
                    .filter(r -> r.getIsDefault() != null && r.getIsDefault() == 1).toList();
            if (defaults.size() == 1) {
                picked = defaults.get(0);
            } else if (resumes.size() == 1) {
                picked = resumes.get(0);
            }
        }
        if (picked == null) {
            String names = String.join("、", resumes.stream()
                    .map(JobResume::getResumeName).toList());
            return new ResumeResolution(null,
                    "你有多份简历，请告诉我要用哪一份：" + names + "。",
                    List.of("resume"), explicit, targetRoleHint, false, false);
        }
        return new ResumeResolution(picked, null, List.of(), explicit, targetRoleHint,
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
        if (StringUtils.hasText(input.constraintsJson())) {
            try {
                previous = objectMapper.readValue(input.constraintsJson(), SearchConstraints.class);
            } catch (Exception ignored) {
                previous = null;
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
            log.warn("产物保存失败，type={}, error={}", type, exception.getMessage());
            return null;
        }
    }

    // 复用已完成的画像产物（简历未变才复用）
    private ResumeProfile loadProfileArtifact(JobSeekInput input, String resumeVersion) {
        try {
            AgentArtifact artifact = agentArtifactService.findLatest(
                    input.userId(), input.planId(), AgentArtifactService.TYPE_RESUME_PROFILE, resumeVersion);
            if (artifact == null || !StringUtils.hasText(artifact.getContentJson())) {
                return null;
            }
            return objectMapper.readValue(artifact.getContentJson(), ResumeProfile.class);
        } catch (Exception exception) {
            log.warn("画像产物解析失败，按未命中处理，error={}", exception.getMessage());
            return null;
        }
    }

    // 复用已完成的候选产物（条件版本未变才复用）
    private List<JobCandidates.JobCandidateItem> loadCandidatesArtifact(JobSeekInput input, String constraintVersion) {
        try {
            AgentArtifact artifact = agentArtifactService.findLatest(
                    input.userId(), input.planId(), AgentArtifactService.TYPE_JOB_CANDIDATES, constraintVersion);
            if (artifact == null || !StringUtils.hasText(artifact.getContentJson())) {
                return null;
            }
            List<Map<String, Object>> raw = objectMapper.readValue(artifact.getContentJson(),
                    objectMapper.getTypeFactory().constructCollectionType(List.class, Map.class));
            List<JobCandidates.JobCandidateItem> items = new ArrayList<>();
            for (Map<String, Object> item : raw) {
                Object jobId = item.containsKey("jobId") ? item.get("jobId") : item.get("id");
                Long id = jobId instanceof Number number ? number.longValue() : null;
                if (id != null) {
                    items.add(new JobCandidates.JobCandidateItem(id, item));
                }
            }
            return items.isEmpty() ? null : items;
        } catch (Exception exception) {
            log.warn("候选产物解析失败，按未命中处理，error={}", exception.getMessage());
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

    // ---------- 工具方法 ----------

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
