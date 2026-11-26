package com.job.bootstrap.agent.scenario.resume;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.job.bootstrap.agent.core.react.TerminalContract;
import com.job.bootstrap.ai.turn.AiToolSpec;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 功能: 简历画像终结契约 finish_resume_profile（第二阶段）。
 *
 * 第一层校验（节点还会做证据回溯校验）:
 * 1. resumeId 必须给出；
 * 2. outcome 只能是 COMPLETED / NEED_INPUT / PARTIAL；
 * 3. NEED_INPUT 必须给出具体问题；
 * 4. evidence 每条必须有 fact 与 quote（quote 由节点回查原文）。
 */
@Component
public class ResumeProfileContract implements TerminalContract<ResumeProfile> {

    public static final String TOOL_NAME = "finish_resume_profile";

    private final ObjectMapper objectMapper;

    public ResumeProfileContract(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String toolName() {
        return TOOL_NAME;
    }

    @Override
    public AiToolSpec spec() {
        Map<String, Object> properties = Map.of(
                "resumeId", Map.of("type", "integer", "description", "当前任务授权的简历ID"),
                "targetRole", Map.of("type", "string", "description", "目标方向，可为空"),
                "targetRoleInferred", Map.of("type", "boolean", "description", "目标方向是否由你推断"),
                "skills", Map.of("type", "array", "items", Map.of("type", "string")),
                "experiences", Map.of("type", "array", "items", Map.of("type", "string")),
                "evidence", Map.of("type", "array", "items", Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "fact", Map.of("type", "string"),
                                "sectionRef", Map.of("type", "string"),
                                "quote", Map.of("type", "string"))),
                        "description", "每条事实必须配一段简历原文引用"),
                "missingInformation", Map.of("type", "array", "items", Map.of("type", "string")),
                "uncertainties", Map.of("type", "array", "items", Map.of("type", "string")),
                "outcome", Map.of("type", "string", "enum", List.of("COMPLETED", "NEED_INPUT", "PARTIAL")),
                "question", Map.of("type", "string")
        );
        return new AiToolSpec(TOOL_NAME,
                "提交简历画像。事实必须来自简历原文，evidence.quote 必须是原文片段；缺失信息写进 missingInformation，不要编造。",
                Map.of("type", "object", "properties", properties,
                        "required", List.of("resumeId", "outcome", "skills", "evidence")));
    }

    @Override
    public TerminalParseResult<ResumeProfile> parseTyped(String argumentsJson) {
        if (!StringUtils.hasText(argumentsJson)) {
            return TerminalParseResult.error("画像参数为空。");
        }
        Map<String, Object> args;
        try {
            args = objectMapper.readValue(argumentsJson, Map.class);
        } catch (Exception exception) {
            return TerminalParseResult.error("画像参数不是合法 JSON。");
        }

        Long resumeId = longOf(args.get("resumeId"));
        String outcome = text(args.get("outcome"));
        String question = text(args.get("question"));
        List<String> skills = strings(args.get("skills"));
        List<String> experiences = strings(args.get("experiences"));
        List<String> missing = strings(args.get("missingInformation"));
        List<String> uncertainties = strings(args.get("uncertainties"));
        List<ResumeProfile.Evidence> evidence = evidences(args.get("evidence"));

        if (resumeId == null || resumeId <= 0) {
            return TerminalParseResult.error("缺少 resumeId。");
        }
        if (!List.of("COMPLETED", "NEED_INPUT", "PARTIAL").contains(outcome)) {
            return TerminalParseResult.error("outcome 只能是 COMPLETED/NEED_INPUT/PARTIAL。");
        }
        if ("NEED_INPUT".equals(outcome) && !StringUtils.hasText(question)) {
            return TerminalParseResult.error("NEED_INPUT 必须提供具体问题。");
        }
        if ("COMPLETED".equals(outcome) && evidence.isEmpty()) {
            return TerminalParseResult.error("COMPLETED 必须至少提供一条原文证据。");
        }

        return TerminalParseResult.ok(new ResumeProfile(
                resumeId,
                null,
                null,
                text(args.get("targetRole")),
                Boolean.TRUE.equals(args.get("targetRoleInferred")),
                skills,
                experiences,
                evidence,
                missing,
                uncertainties,
                outcome,
                question
        ));
    }

    @Override
    public String outcomeOf(ResumeProfile proposal) {
        return proposal == null ? "FAILED" : proposal.outcome();
    }

    @Override
    public String summaryOf(ResumeProfile proposal) {
        return proposal == null ? "" : "简历画像：" + proposal.skills().size() + " 项技能 / "
                + proposal.experiences().size() + " 条经历";
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

    private String text(Object value) {
        return value == null ? null : String.valueOf(value).trim();
    }

    private List<String> strings(Object value) {
        List<String> result = new ArrayList<>();
        if (value instanceof List<?> list) {
            for (Object item : list) {
                if (item != null && StringUtils.hasText(String.valueOf(item))) {
                    result.add(String.valueOf(item).trim());
                }
            }
        }
        return result;
    }

    private List<ResumeProfile.Evidence> evidences(Object value) {
        List<ResumeProfile.Evidence> result = new ArrayList<>();
        if (value instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    String fact = text(map.get("fact"));
                    String quote = text(map.get("quote"));
                    if (StringUtils.hasText(fact)) {
                        result.add(new ResumeProfile.Evidence(fact, text(map.get("sectionRef")), quote));
                    }
                }
            }
        }
        return result;
    }
}
