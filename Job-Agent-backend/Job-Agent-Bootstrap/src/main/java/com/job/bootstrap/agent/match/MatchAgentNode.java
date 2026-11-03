package com.job.bootstrap.agent.match;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.job.bootstrap.agent.react.AgentDefinition;
import com.job.bootstrap.agent.react.AgentRunContext;
import com.job.bootstrap.agent.react.AgentRunExecutionHooks;
import com.job.bootstrap.agent.react.ReActRunner;
import com.job.bootstrap.agent.resume.ResumeProfile;
import com.job.bootstrap.agent.resume.ResumeSections;
import com.job.bootstrap.agent.run.AgentRunService;
import com.job.bootstrap.agent.search.JobCandidates;
import com.job.bootstrap.agent.search.SearchConstraints;
import com.job.bootstrap.ai.turn.AiChatMessage;
import com.job.bootstrap.ai.turn.AiToolSpec;
import com.job.bootstrap.service.JobMatchService;
import com.job.bootstrap.service.JobPositionService;
import com.job.common.entity.agent.AgentRun;
import com.job.common.entity.position.JobPosition;
import com.job.common.entity.resume.JobResume;
import com.job.common.vo.match.JobMatchVO;
import com.job.enums.AgentRunStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 功能: 岗位匹配节点 MatchAgent（第二阶段）。
 *
 * 职责:
 * 1. 在真实工具反馈循环内读取候选岗位详情（read_job_detail）、复用规则计算做只读预览
 *    （preview_job_match，不写匹配记录）、按需补读简历证据（read_resume_evidence）；
 * 2. 代码侧校验（模型骗不过去）:
 *    - 排名项 jobId 必须来自当前有效候选，不能跨用户/跨批次；
 *    - 硬条件不合格的岗位不得进入最终推荐集合；
 *    - rank 必须连续（1..n），数量不超过 Top-K；
 *    - ruleScore 以服务端预览结果为准（模型给的分数不采信）；
 * 3. 缺口只表达“岗位要求但简历未见证据”，不断言用户不会。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MatchAgentNode {

    public static final String NODE_KEY = "MATCH_AGENT";
    public static final String AGENT_TYPE = "MATCH";
    public static final String TOOL_READ_JOB = "match.read_job_detail";
    public static final String TOOL_PREVIEW = "match.preview_job_match";
    public static final String TOOL_READ_EVIDENCE = "match.read_resume_evidence";

    private static final int MAX_TURNS = 5;
    private static final int MAX_TOOL_CALLS = 10;
    private static final long DEADLINE_SECONDS = 90L;

    private final ReActRunner reActRunner;
    private final MatchReportContract matchReportContract;
    private final AgentRunService agentRunService;
    private final JobPositionService jobPositionService;
    private final JobMatchService jobMatchService;
    private final ObjectMapper objectMapper;

    /**
     * 节点结果。
     */
    public record Result(MatchReport report, String reasonCode, String status, Long runId,
                         List<AiChatMessage> messages, int toolCalls, int turns) {
    }

    /**
     * 执行匹配节点。
     *
     * @param candidates        当前有效候选（已去重、满足硬条件由节点再复核）
     * @param topK              最终推荐数量上限
     * @param constraintVersion 条件版本
     */
    public Result execute(Long userId, Long conversationId, Long planId, String traceId,
                          JobResume resume, ResumeProfile profile, List<JobCandidates.JobCandidateItem> candidates,
                          SearchConstraints constraints, int topK, String constraintVersion,
                          Long profileArtifactId, Long candidatesArtifactId) {
        Set<Long> candidateIds = candidates.stream()
                .map(JobCandidates.JobCandidateItem::jobId).collect(Collectors.toSet());
        Map<Long, BigDecimal> previewedScores = new HashMap<>();
        List<ResumeSections.Section> sections = ResumeSections.of(resume, objectMapper);

        AgentRun run = agentRunService.createForNode(userId, conversationId, planId, traceId,
                "match-agent", "岗位匹配：" + candidates.size() + " 个候选", null,
                NODE_KEY, AGENT_TYPE, constraintVersion);
        int attempt = 1;
        if (!agentRunService.claim(run.getId(), 0)) {
            return new Result(null, "NODE_CLAIM_REJECTED", "RUNNING", run.getId(), List.of(), 0, 0);
        }

        AgentRunExecutionHooks.set(
                System.currentTimeMillis() + DEADLINE_SECONDS * 1000,
                messages -> agentRunService.saveMessages(run.getId(), attempt, toJson(messages))
        );
        try {
            AgentDefinition definition = definition(candidates, profile, topK, sections);
            AgentRunContext context = new AgentRunContext(definition.systemPrompt(), userPrompt(candidates.size(), topK));

            ReActRunner.ReActRunResult<MatchReport> result = reActRunner.runWithTerminal(
                    definition, context,
                    (tool, args) -> executeTool(tool, args, candidateIds, sections, resume,
                            constraints, userId, previewedScores),
                    userId, traceId, matchReportContract);

            if (!result.succeeded()) {
                agentRunService.finish(run.getId(), attempt, AgentRunStatus.FAILED, null,
                        toJson(context.messages()), result.reasonCode());
                return new Result(null, result.reasonCode(), "FAILED", run.getId(),
                        context.messages(), result.toolCallsUsed(), result.turnsUsed());
            }

            // 校验 + 用服务端事实补全报告
            MatchReport raw = result.payload();
            List<MatchReport.RankedItem> verified = new ArrayList<>();
            Set<Long> seen = new java.util.HashSet<>();
            for (MatchReport.RankedItem item : raw.rankedItems()) {
                if (item.jobId() == null || !candidateIds.contains(item.jobId()) || !seen.add(item.jobId())) {
                    return fail(run.getId(), attempt, context, "REPORT_JOB_NOT_IN_CANDIDATES");
                }
                JobCandidates.JobCandidateItem candidate = candidates.stream()
                        .filter(c -> c.jobId().equals(item.jobId())).findFirst().orElse(null);
                if (candidate == null || !constraints.accepts(candidate)) {
                    return fail(run.getId(), attempt, context, "REPORT_JOB_VIOLATES_HARD_CONSTRAINT");
                }
                // 规则分以服务端预览为准，模型自报分数不采信
                BigDecimal serverScore = previewedScores.get(item.jobId());
                verified.add(new MatchReport.RankedItem(
                        item.jobId(), verified.size() + 1, serverScore,
                        item.matchPoints(), item.gaps(), item.suggestions(), item.evidenceRefs()));
            }
            if (verified.size() > topK) {
                verified = new ArrayList<>(verified.subList(0, topK));
            }

            MatchReport report = new MatchReport(
                    profileArtifactId, candidatesArtifactId, constraintVersion,
                    verified, raw.uncertainties(), verified.size(), candidates.size(),
                    verified.size() < candidates.size() ? "PARTIAL" : "COMPLETED");

            String outcome = "COMPLETED".equals(raw.outcome()) && "PARTIAL".equals(report.outcome())
                    ? "PARTIAL" : raw.outcome();
            MatchReport finalReport = new MatchReport(
                    profileArtifactId, candidatesArtifactId, constraintVersion,
                    verified, raw.uncertainties(), verified.size(), candidates.size(), outcome);

            agentRunService.finish(run.getId(), attempt, AgentRunStatus.SUCCEEDED,
                    toJson(finalReport), toJson(context.messages()), null);

            return new Result(finalReport, null, AgentRunStatus.SUCCEEDED.name(), run.getId(),
                    context.messages(), result.toolCallsUsed(), result.turnsUsed());
        } catch (Exception exception) {
            log.warn("匹配节点异常，error={}", exception.getMessage());
            agentRunService.finish(run.getId(), attempt, AgentRunStatus.FAILED, null, null,
                    "NODE_EXCEPTION:" + exception.getMessage());
            return new Result(null, "NODE_EXCEPTION", "FAILED", run.getId(), List.of(), 0, 0);
        } finally {
            AgentRunExecutionHooks.clear();
        }
    }

    private Result fail(Long runId, int attempt, AgentRunContext context, String reasonCode) {
        agentRunService.finish(runId, attempt, AgentRunStatus.FAILED, null,
                toJson(context.messages()), reasonCode);
        return new Result(null, reasonCode, "FAILED", runId, context.messages(),
                context.toolCalls(), context.turn());
    }

    private AgentDefinition definition(List<JobCandidates.JobCandidateItem> candidates,
                                       ResumeProfile profile, int topK,
                                       List<ResumeSections.Section> sections) {
        Map<String, String> aliases = new LinkedHashMap<>();
        aliases.put("read_job_detail", TOOL_READ_JOB);
        aliases.put("preview_job_match", TOOL_PREVIEW);
        aliases.put("read_resume_evidence", TOOL_READ_EVIDENCE);

        Map<String, Object> jobArgs = new LinkedHashMap<>();
        jobArgs.put("jobId", Map.of("type", "integer"));

        return new AgentDefinition(
                "match-agent",
                systemPrompt(candidates, profile, topK, sections),
                aliases,
                List.of(
                        new AiToolSpec("read_job_detail", "读取候选岗位的 JD 与要求",
                                Map.of("type", "object", "properties", jobArgs, "required", List.of("jobId"))),
                        new AiToolSpec("preview_job_match", "复用规则引擎预览某候选的匹配分与命中/缺失技能（不落库）",
                                Map.of("type", "object", "properties", jobArgs, "required", List.of("jobId"))),
                        new AiToolSpec("read_resume_evidence", "按 sectionId 补读简历原文证据",
                                Map.of("type", "object", "properties",
                                        Map.of("sectionId", Map.of("type", "string")), "required", List.of("sectionId"))),
                        matchReportContract.spec()
                ),
                MatchReportContract.TOOL_NAME,
                MAX_TURNS,
                MAX_TOOL_CALLS,
                DEADLINE_SECONDS
        );
    }

    private String systemPrompt(List<JobCandidates.JobCandidateItem> candidates,
                                ResumeProfile profile, int topK,
                                List<ResumeSections.Section> sections) {
        StringBuilder candidateList = new StringBuilder();
        int index = 1;
        for (JobCandidates.JobCandidateItem candidate : candidates) {
            String title = String.valueOf(candidate.raw().getOrDefault("jobTitle", ""));
            String city = String.valueOf(candidate.raw().getOrDefault("city", ""));
            candidateList.append(index++).append(". jobId=").append(candidate.jobId())
                    .append(" ").append(title).append(" @ ").append(city).append("\n");
        }
        String skills = String.join("、", profile.skills());
        String sectionIndex = sections.stream()
                .map(s -> s.sectionId() + " " + s.title()).collect(Collectors.joining("；"));

        return """
                你是岗位匹配 Agent。任务：对**当前候选集合**做证据化比较，用 finish_match_report 提交排序与差距。
                规则：
                1. jobId 只能来自下列候选，不能引用其他岗位；候选之外的一律无效。
                2. 可调用 read_job_detail 读 JD、preview_job_match 取规则分、read_resume_evidence 补读简历原文。
                3. matchPoints 要对应简历证据（evidenceRefs 写段落 S1..Sn 或 JD 字段）；gaps 只写“岗位要求但简历未见证据”，
                   不要写“用户不会”。
                4. rank 从 1 开始连续，最多 %d 个；不足就少给，不要凑数。
                5. finish_match_report 必须单独一轮提交；你只做比较和建议，不能声称已投递/已联系公司。
                简历技能：%s
                简历分段：%s
                候选列表：
                %s
                """.formatted(topK, StringUtils.hasText(skills) ? skills : "（画像未提取到技能）",
                sectionIndex, candidateList);
    }

    private String userPrompt(int candidateCount, int topK) {
        return "请从这 " + candidateCount + " 个候选里选出最合适的（最多 " + topK + " 个），给出匹配点、缺口和准备建议。";
    }

    private String executeTool(String tool, Map<String, Object> args, Set<Long> candidateIds,
                               List<ResumeSections.Section> sections, JobResume resume,
                               SearchConstraints constraints, Long userId,
                               Map<Long, BigDecimal> previewedScores) throws Exception {
        if (TOOL_READ_EVIDENCE.equals(tool)) {
            String sectionId = String.valueOf(args.getOrDefault("sectionId", ""));
            ResumeSections.Section section = ResumeSections.find(sections, sectionId);
            if (section == null) {
                throw new IllegalArgumentException("sectionId 不存在：" + sectionId);
            }
            return objectMapper.writeValueAsString(Map.of(
                    "sectionId", section.sectionId(), "title", section.title(), "text", section.text()));
        }

        Long jobId = longOf(args.get("jobId"));
        if (jobId == null || !candidateIds.contains(jobId)) {
            throw new IllegalArgumentException("jobId 不在当前候选集合：" + args.get("jobId"));
        }
        JobPosition job = jobPositionService.getById(jobId);
        if (job == null) {
            throw new IllegalArgumentException("岗位不存在：" + jobId);
        }

        if (TOOL_READ_JOB.equals(tool)) {
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("jobId", job.getId());
            detail.put("jobTitle", job.getJobTitle());
            detail.put("city", job.getCity());
            detail.put("minSalary", job.getMinSalary());
            detail.put("maxSalary", job.getMaxSalary());
            detail.put("educationReq", job.getEducationReq());
            detail.put("experienceReq", job.getExperienceReq());
            detail.put("skillKeywords", job.getSkillKeywords());
            detail.put("jobDescription", clip(job.getJobDescription()));
            detail.put("jobRequirement", clip(job.getJobRequirement()));
            return objectMapper.writeValueAsString(detail);
        }
        if (TOOL_PREVIEW.equals(tool)) {
            Map<String, Object> explicit = new LinkedHashMap<>();
            if (constraints != null) {
                if (StringUtils.hasText(constraints.city())) {
                    explicit.put("city", constraints.city());
                }
                if (constraints.minSalary() != null) {
                    explicit.put("minSalary", constraints.minSalary());
                }
                if (StringUtils.hasText(constraints.educationReq())) {
                    explicit.put("educationReq", constraints.educationReq());
                }
            }
            JobMatchVO preview = jobMatchService.previewMatch(userId, resume.getId(), jobId, explicit);
            if (preview != null && preview.getMatchScore() != null) {
                previewedScores.put(jobId, preview.getMatchScore());
            }
            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("jobId", jobId);
            summary.put("matchScore", preview == null ? null : preview.getMatchScore());
            summary.put("matchLevel", preview == null ? null : preview.getMatchLevel());
            summary.put("recommendApply", preview == null ? null : preview.getRecommendApply());
            summary.put("matchedSkills", preview == null ? List.of() : preview.getMatchedSkills());
            summary.put("missingSkills", preview == null ? List.of() : preview.getMissingSkills());
            summary.put("riskPoints", preview == null ? List.of() : preview.getRiskPoints());
            summary.put("evidences", preview == null ? List.of() : preview.getEvidences());
            return objectMapper.writeValueAsString(summary);
        }
        throw new IllegalArgumentException("工具不在匹配白名单：" + tool);
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

    private String clip(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        String trimmed = text.trim();
        return trimmed.length() > 800 ? trimmed.substring(0, 800) + "…" : trimmed;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            return null;
        }
    }
}
