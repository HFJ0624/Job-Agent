package com.job.bootstrap.agent.resume;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.job.bootstrap.agent.react.AgentDefinition;
import com.job.bootstrap.agent.react.AgentRunContext;
import com.job.bootstrap.agent.react.AgentRunExecutionHooks;
import com.job.bootstrap.agent.react.ReActRunner;
import com.job.bootstrap.agent.run.AgentRunService;
import com.job.bootstrap.ai.turn.AiChatMessage;
import com.job.bootstrap.ai.turn.AiToolSpec;
import com.job.common.entity.agent.AgentRun;
import com.job.common.entity.resume.JobResume;
import com.job.enums.AgentRunStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 功能: 简历画像节点 ResumeAgent（第二阶段）。
 *
 * 职责:
 * 1. 在真实工具反馈循环内读取授权简历（read_resume / read_resume_section），
 *    通过 finish_resume_profile 提交结构化画像；
 * 2. **证据回溯校验**：每条 evidence.quote 必须能在简历原文中找到（归一化空白后包含），
 *    找不到即整单失败——从机制上禁止编造项目/技术/量化指标；
 * 3. 画像事实只来自简历；“简历没有证据”写进 missingInformation，不下“用户不会”的结论。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ResumeAgentNode {

    public static final String NODE_KEY = "RESUME_AGENT";
    public static final String AGENT_TYPE = "RESUME";
    public static final String TOOL_READ_RESUME = "resume.read_resume";
    public static final String TOOL_READ_SECTION = "resume.read_resume_section";

    /** 节点预算（比父任务更紧）。 */
    private static final int MAX_TURNS = 4;
    private static final int MAX_TOOL_CALLS = 6;
    private static final long DEADLINE_SECONDS = 60L;

    private final ReActRunner reActRunner;
    private final ResumeProfileContract resumeProfileContract;
    private final AgentRunService agentRunService;
    private final ObjectMapper objectMapper;

    /**
     * 节点结果。
     */
    public record Result(ResumeProfile profile, String reasonCode, String status, Long runId,
                         List<AiChatMessage> messages, int toolCalls, int turns) {
    }

    /**
     * 执行画像节点。
     *
     * @param resume        已授权并解析完成的简历（归属校验在 PREPARE_CONTEXT 完成）
     * @param resumeVersion 简历版本（变更即画像失效）
     * @param targetRoleHint 用户明确的目标方向（可空）
     */
    public Result execute(Long userId, Long conversationId, Long planId, String traceId,
                          JobResume resume, String resumeVersion, String targetRoleHint) {
        List<ResumeSections.Section> sections = ResumeSections.of(resume, objectMapper);
        String rawResume = normalize(resume.getRawText());

        AgentRun run = agentRunService.createForNode(userId, conversationId, planId, traceId,
                "resume-agent", "简历画像：" + resume.getResumeName(), null,
                NODE_KEY, AGENT_TYPE, resumeVersion);
        int attempt = 1;
        if (!agentRunService.claim(run.getId(), 0)) {
            return new Result(null, "NODE_CLAIM_REJECTED", "RUNNING", run.getId(), List.of(), 0, 0);
        }

        AgentRunExecutionHooks.set(
                System.currentTimeMillis() + DEADLINE_SECONDS * 1000,
                messages -> agentRunService.saveMessages(run.getId(), attempt, toJson(messages))
        );
        try {
            AgentDefinition definition = definition(sections, targetRoleHint);
            AgentRunContext context = new AgentRunContext(definition.systemPrompt(), userPrompt(resume, targetRoleHint));

            ReActRunner.ReActRunResult<ResumeProfile> result = reActRunner.runWithTerminal(
                    definition, context,
                    (tool, args) -> executeTool(tool, args, sections, resume),
                    userId, traceId, resumeProfileContract);

            if (!result.succeeded()) {
                agentRunService.finish(run.getId(), attempt, AgentRunStatus.FAILED, null,
                        toJson(context.messages()), result.reasonCode());
                return new Result(null, result.reasonCode(), "FAILED", run.getId(),
                        context.messages(), result.toolCallsUsed(), result.turnsUsed());
            }

            // 证据回溯校验 + 归属/版本补全
            ResumeProfile profile = result.payload();
            if (!resume.getId().equals(profile.resumeId())) {
                agentRunService.finish(run.getId(), attempt, AgentRunStatus.FAILED, null,
                        toJson(context.messages()), "PROFILE_RESUME_MISMATCH");
                return new Result(null, "PROFILE_RESUME_MISMATCH", "FAILED", run.getId(),
                        context.messages(), result.toolCallsUsed(), result.turnsUsed());
            }
            List<ResumeProfile.Evidence> invalid = new ArrayList<>();
            for (ResumeProfile.Evidence evidence : profile.evidence()) {
                String quote = normalize(evidence.quote());
                if (!StringUtils.hasText(quote) || !rawResume.contains(quote)) {
                    invalid.add(evidence);
                }
            }
            if (!invalid.isEmpty()) {
                agentRunService.finish(run.getId(), attempt, AgentRunStatus.FAILED, null,
                        toJson(context.messages()), "EVIDENCE_NOT_IN_RESUME");
                return new Result(null, "EVIDENCE_NOT_IN_RESUME", "FAILED", run.getId(),
                        context.messages(), result.toolCallsUsed(), result.turnsUsed());
            }

            ResumeProfile enriched = new ResumeProfile(
                    profile.resumeId(), resumeVersion, resume.getResumeName(),
                    profile.targetRole(), profile.targetRoleInferred(),
                    profile.skills(), profile.experiences(), profile.evidence(),
                    profile.missingInformation(), profile.uncertainties(),
                    profile.outcome(), profile.question());

            AgentRunStatus status = "NEED_INPUT".equals(profile.outcome())
                    ? AgentRunStatus.WAITING_INPUT : AgentRunStatus.SUCCEEDED;
            agentRunService.finish(run.getId(), attempt, status, toJson(enriched),
                    toJson(context.messages()),
                    "NEED_INPUT".equals(profile.outcome()) ? "NEED_INPUT" : null);

            return new Result(enriched, null, status.name(), run.getId(),
                    context.messages(), result.toolCallsUsed(), result.turnsUsed());
        } catch (Exception exception) {
            log.warn("简历画像节点异常，error={}", exception.getMessage());
            agentRunService.finish(run.getId(), attempt, AgentRunStatus.FAILED, null, null,
                    "NODE_EXCEPTION:" + exception.getMessage());
            return new Result(null, "NODE_EXCEPTION", "FAILED", run.getId(), List.of(), 0, 0);
        } finally {
            AgentRunExecutionHooks.clear();
        }
    }

    private AgentDefinition definition(List<ResumeSections.Section> sections, String targetRoleHint) {
        Map<String, String> aliases = new LinkedHashMap<>();
        aliases.put("read_resume", TOOL_READ_RESUME);
        aliases.put("read_resume_section", TOOL_READ_SECTION);

        Map<String, Object> sectionProps = new LinkedHashMap<>();
        sectionProps.put("sectionId", Map.of("type", "string", "description", "段落标识，如 S1"));

        return new AgentDefinition(
                "resume-agent",
                systemPrompt(sections, targetRoleHint),
                aliases,
                List.of(
                        new AiToolSpec("read_resume", "读取当前任务授权简历的摘要与分段索引",
                                Map.of("type", "object", "properties", Map.of())),
                        new AiToolSpec("read_resume_section", "按 sectionId 读取简历某一段原文",
                                Map.of("type", "object", "properties", sectionProps,
                                        "required", List.of("sectionId"))),
                        resumeProfileContract.spec()
                ),
                ResumeProfileContract.TOOL_NAME,
                MAX_TURNS,
                MAX_TOOL_CALLS,
                DEADLINE_SECONDS
        );
    }

    private String systemPrompt(List<ResumeSections.Section> sections, String targetRoleHint) {
        StringBuilder index = new StringBuilder();
        for (ResumeSections.Section section : sections) {
            index.append("- ").append(section.sectionId()).append(" ").append(section.title()).append("\n");
        }
        return """
                你是简历画像 Agent。任务：从**授权简历原文**提取求职画像，并用 finish_resume_profile 提交。
                规则：
                1. 只能用 read_resume / read_resume_section 读取当前简历；不要引用其他简历或编造内容。
                2. skills 和 experiences 必须能在简历中找到；每条事实配 evidence（quote 必须**逐字复制**原文片段，不得改写）。
                3. “简历没有证据”写进 missingInformation，不要断言用户不会某技能。
                4. 目标方向已知就填入；若是你推断的，targetRoleInferred=true。只在确实无法判断时用 NEED_INPUT。
                5. finish_resume_profile 必须单独一轮提交。
                可用分段：
                %s
                用户明确的目标方向：%s
                """.formatted(index, StringUtils.hasText(targetRoleHint) ? targetRoleHint : "未指定");
    }

    private String userPrompt(JobResume resume, String targetRoleHint) {
        return "请为简历《" + resume.getResumeName() + "》生成求职画像。"
                + (StringUtils.hasText(targetRoleHint) ? "目标方向：" + targetRoleHint + "。" : "");
    }

    private String executeTool(String tool, Map<String, Object> args,
                               List<ResumeSections.Section> sections, JobResume resume) throws Exception {
        if (TOOL_READ_RESUME.equals(tool)) {
            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("resumeId", resume.getId());
            summary.put("resumeName", resume.getResumeName());
            summary.put("chars", resume.getRawText() == null ? 0 : resume.getRawText().length());
            summary.put("sections", sections.stream()
                    .map(s -> Map.of("sectionId", s.sectionId(), "title", s.title(), "chars", s.text().length()))
                    .toList());
            return objectMapper.writeValueAsString(summary);
        }
        if (TOOL_READ_SECTION.equals(tool)) {
            String sectionId = String.valueOf(args.getOrDefault("sectionId", ""));
            ResumeSections.Section section = ResumeSections.find(sections, sectionId);
            if (section == null) {
                throw new IllegalArgumentException("sectionId 不存在：" + sectionId);
            }
            return objectMapper.writeValueAsString(Map.of(
                    "sectionId", section.sectionId(),
                    "title", section.title(),
                    "text", section.text()));
        }
        throw new IllegalArgumentException("工具不在简历画像白名单：" + tool);
    }

    // 归一化空白，用于证据回溯比对（换行/多余空格不影响包含判断）
    private String normalize(String text) {
        return text == null ? "" : text.replaceAll("\\s+", "");
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            return null;
        }
    }
}
