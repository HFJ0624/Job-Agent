package com.job.bootstrap.agent.runtime.react;

import com.job.bootstrap.ai.turn.AiToolSpec;
import com.job.bootstrap.ai.turn.AiToolCall;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 功能: 终结工具契约（第一阶段：finish_job_search）。
 *
 * 设计说明:
 * 1. 终结工具是**唯一的业务结果出口**：模型不能靠自由文本宣称成功，
 *    必须通过结构化工具提交 outcome/candidateIds 等字段；
 * 2. 这里只做“结构与业务约束”的第一层校验（FOUND 至少一个候选、NEED_INPUT 必须有问题和缺失字段等），
 *    **候选真伪、条件符合性由 Workflow 用真实工具结果复核**——双重校验，模型两侧都骗不过去；
 * 3. 非法参数返回错误文案，由 Runner 触发一次修正机会。
 */
@Component
public class TerminalToolContract implements TerminalContract<TerminalToolContract.TerminalProposal> {

    public static final String TOOL_NAME = "finish_job_search";

    private final ObjectMapper objectMapper;

    public TerminalToolContract(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String toolName() {
        return TOOL_NAME;
    }

    /** 对模型暴露的工具定义（与 AgentDefinition.toolSpecs 一起下发）。 */
    @Override
    public AiToolSpec spec() {
        Map<String, Object> properties = Map.of(
                "outcome", Map.of("type", "string", "enum", List.of("FOUND", "EMPTY", "NEED_INPUT", "FAILED")),
                "candidateIds", Map.of("type", "array", "items", Map.of("type", "integer")),
                "missingFields", Map.of("type", "array", "items", Map.of("type", "string")),
                "question", Map.of("type", "string"),
                "reasonCode", Map.of("type", "string"),
                "summary", Map.of("type", "string")
        );
        return new AiToolSpec(
                TOOL_NAME,
                "提交岗位搜索的最终业务结果。必须单独一轮提交，不能和业务查询工具同轮。",
                Map.of("type", "object", "properties", properties, "required", List.of("outcome", "summary"))
        );
    }

    /** 解析成功时的提议（仍是提议，需 Workflow 复核）。 */
    public record TerminalProposal(
            String outcome,
            List<Long> candidateIds,
            List<String> missingFields,
            String question,
            String reasonCode,
            String summary
    ) {
    }

    /** 解析结果：错误文案非空时表示非法，需要模型修正。 */
    public record ParseResult(TerminalProposal proposal, String error) {
    }

    @Override
    public TerminalParseResult<TerminalProposal> parseTyped(String argumentsJson) {
        ParseResult legacy = parse(argumentsJson);
        return legacy.error() == null
                ? TerminalParseResult.ok(legacy.proposal())
                : TerminalParseResult.error(legacy.error());
    }

    @Override
    public String outcomeOf(TerminalProposal proposal) {
        return proposal == null ? "FAILED" : proposal.outcome();
    }

    @Override
    public String summaryOf(TerminalProposal proposal) {
        return proposal == null ? "" : proposal.summary();
    }

    /**
     * 解析并做第一层业务约束校验（兼容旧调用）。
     */
    public ParseResult parse(String argumentsJson) {
        if (!StringUtils.hasText(argumentsJson)) {
            return new ParseResult(null, "终结参数为空，请提交 outcome 与 summary。");
        }
        Map<String, Object> args;
        try {
            args = objectMapper.readValue(argumentsJson, Map.class);
        } catch (Exception exception) {
            return new ParseResult(null, "终结参数不是合法 JSON，请重新提交。");
        }

        String outcome = text(args.get("outcome"));
        String summary = text(args.get("summary"));
        if (!StringUtils.hasText(outcome)) {
            return new ParseResult(null, "缺少 outcome。");
        }
        List<Long> candidateIds = ids(args.get("candidateIds"));
        List<String> missingFields = strings(args.get("missingFields"));
        String question = text(args.get("question"));
        String reasonCode = text(args.get("reasonCode"));

        switch (outcome) {
            case "FOUND" -> {
                if (candidateIds.isEmpty()) {
                    return new ParseResult(null, "FOUND 必须至少提交一个 candidateId。");
                }
            }
            case "EMPTY" -> {
                if (!candidateIds.isEmpty()) {
                    return new ParseResult(null, "EMPTY 不能携带 candidateIds。");
                }
                if (!StringUtils.hasText(summary)) {
                    return new ParseResult(null, "EMPTY 必须说明 summary（查了什么、为什么没有结果）。");
                }
            }
            case "NEED_INPUT" -> {
                if (!StringUtils.hasText(question) || missingFields.isEmpty()) {
                    return new ParseResult(null, "NEED_INPUT 必须提供 question 与 missingFields。");
                }
            }
            case "FAILED" -> {
                if (!StringUtils.hasText(reasonCode) && !StringUtils.hasText(summary)) {
                    return new ParseResult(null, "FAILED 必须说明原因（reasonCode 或 summary）。");
                }
            }
            default -> {
                return new ParseResult(null, "outcome 只能是 FOUND/EMPTY/NEED_INPUT/FAILED。");
            }
        }

        return new ParseResult(
                new TerminalProposal(outcome, candidateIds, missingFields, question, reasonCode, summary),
                null
        );
    }

    private String text(Object value) {
        return value == null ? null : String.valueOf(value).trim();
    }

    private List<Long> ids(Object value) {
        List<Long> result = new ArrayList<>();
        if (value instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Number number) {
                    result.add(number.longValue());
                } else if (item instanceof String text && StringUtils.hasText(text)) {
                    try {
                        result.add(Long.parseLong(text.trim()));
                    } catch (NumberFormatException ignored) {
                        // 非数字候选 ID 丢弃，由 Workflow 复核时进一步拦截
                    }
                }
            }
        }
        return result;
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
}
