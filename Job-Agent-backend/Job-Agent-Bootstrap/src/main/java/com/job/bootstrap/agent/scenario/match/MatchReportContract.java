package com.job.bootstrap.agent.scenario.match;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.job.bootstrap.agent.runtime.react.TerminalContract;
import com.job.bootstrap.ai.turn.AiToolSpec;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 功能: 匹配报告终结契约 finish_match_report（第二阶段）。
 *
 * 第一层校验（节点还会做候选归属/硬条件复核）:
 * 1. outcome 只能是 COMPLETED / PARTIAL；
 * 2. rankedItems 非空（COMPLETED），jobId 不重复、rank 不重复；
 * 3. 每项必须有至少一条匹配点或缺口（空项无意义）。
 */
@Component
public class MatchReportContract implements TerminalContract<MatchReport> {

    public static final String TOOL_NAME = "finish_match_report";

    private final ObjectMapper objectMapper;

    public MatchReportContract(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String toolName() {
        return TOOL_NAME;
    }

    @Override
    public AiToolSpec spec() {
        Map<String, Object> itemProps = Map.of(
                "jobId", Map.of("type", "integer"),
                "rank", Map.of("type", "integer", "description", "排名，从 1 开始连续"),
                "ruleScore", Map.of("type", "number", "description", "规则分（可空，来自 preview_job_match）"),
                "matchPoints", Map.of("type", "array", "items", Map.of("type", "string")),
                "gaps", Map.of("type", "array", "items", Map.of("type", "string"),
                        "description", "岗位要求但简历未见证据的点，不要写“不会”"),
                "suggestions", Map.of("type", "array", "items", Map.of("type", "string")),
                "evidenceRefs", Map.of("type", "array", "items", Map.of("type", "string"),
                        "description", "证据引用：简历段落 S1..Sn 或 JD 字段名")
        );
        Map<String, Object> properties = Map.of(
                "outcome", Map.of("type", "string", "enum", List.of("COMPLETED", "PARTIAL")),
                "rankedItems", Map.of("type", "array", "items", Map.of(
                        "type", "object", "properties", itemProps,
                        "required", List.of("jobId", "rank"))),
                "uncertainties", Map.of("type", "array", "items", Map.of("type", "string"))
        );
        return new AiToolSpec(TOOL_NAME,
                "提交岗位匹配报告。jobId 只能来自本次候选；排名连续；缺口写“简历未见证据”，不要断言用户不会。",
                Map.of("type", "object", "properties", properties,
                        "required", List.of("outcome", "rankedItems")));
    }

    @Override
    public TerminalParseResult<MatchReport> parseTyped(String argumentsJson) {
        if (!StringUtils.hasText(argumentsJson)) {
            return TerminalParseResult.error("匹配报告为空。");
        }
        Map<String, Object> args;
        try {
            args = objectMapper.readValue(argumentsJson, Map.class);
        } catch (Exception exception) {
            return TerminalParseResult.error("匹配报告不是合法 JSON。");
        }

        String outcome = text(args.get("outcome"));
        if (!List.of("COMPLETED", "PARTIAL").contains(outcome)) {
            return TerminalParseResult.error("outcome 只能是 COMPLETED/PARTIAL。");
        }
        List<MatchReport.RankedItem> items = items(args.get("rankedItems"));
        if (items.isEmpty()) {
            return TerminalParseResult.error("rankedItems 不能为空。");
        }
        Set<Long> ids = new HashSet<>();
        Set<Integer> ranks = new HashSet<>();
        for (MatchReport.RankedItem item : items) {
            if (item.jobId() == null) {
                return TerminalParseResult.error("rankedItems 存在缺少 jobId 的项。");
            }
            if (!ids.add(item.jobId())) {
                return TerminalParseResult.error("rankedItems 中 jobId 重复：" + item.jobId());
            }
            if (item.rank() <= 0 || !ranks.add(item.rank())) {
                return TerminalParseResult.error("rank 必须是正整数且不重复。");
            }
            if (item.matchPoints().isEmpty() && item.gaps().isEmpty()) {
                return TerminalParseResult.error("jobId " + item.jobId() + " 没有任何匹配点或缺口。");
            }
        }

        return TerminalParseResult.ok(new MatchReport(
                null, null, null, items,
                strings(args.get("uncertainties")),
                items.size(), 0, outcome));
    }

    @Override
    public String outcomeOf(MatchReport proposal) {
        return proposal == null ? "FAILED" : proposal.outcome();
    }

    @Override
    public String summaryOf(MatchReport proposal) {
        return proposal == null ? "" : "匹配报告：" + proposal.rankedItems().size() + " 个岗位";
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

    private List<MatchReport.RankedItem> items(Object value) {
        List<MatchReport.RankedItem> result = new ArrayList<>();
        if (!(value instanceof List<?> list)) {
            return result;
        }
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> map)) {
                continue;
            }
            result.add(new MatchReport.RankedItem(
                    longOf(map.get("jobId")),
                    intOf(map.get("rank")),
                    decimalOf(map.get("ruleScore")),
                    strings(map.get("matchPoints")),
                    strings(map.get("gaps")),
                    strings(map.get("suggestions")),
                    strings(map.get("evidenceRefs"))
            ));
        }
        return result;
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

    private int intOf(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        return 0;
    }

    private BigDecimal decimalOf(Object value) {
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        if (value instanceof String text && StringUtils.hasText(text)) {
            try {
                return new BigDecimal(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }
}
