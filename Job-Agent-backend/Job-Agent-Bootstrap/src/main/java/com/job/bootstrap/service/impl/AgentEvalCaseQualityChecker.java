package com.job.bootstrap.service.impl;

import com.job.common.entity.agent.AgentEvalCase;
import com.job.common.vo.agent.AgentEvalCaseQualityReportVO;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Agent Eval 用例质量检查器，负责在回归运行前检查用例配置是否合理。
 *
 * <p>核心职责：
 * 按 evalType 分发到不同规则（TOOL_CALL、RAG_RETRIEVAL、ANSWER_QUALITY、GUARDRAIL、JSON_OUTPUT、END_TO_END），
 * 检查输入是否为空、核心断言是否齐全、关键词是否缺失等质量问题。
 * 只返回问题报告，不修改用例，修复动作仍交给管理员确认。</p>
 *
 * <p>所属业务模块：Job-Agent-Bootstrap 模块下的 Agent Eval 子模块（用例质量检查层）。</p>
 *
 * <p>主要调用链：
 * 后台 -> AgentEvalService.checkCaseQuality
 * -> AgentEvalCaseQualityChecker.check
 * -> checkCase（按 evalType 分发）
 * -> addIssue（记录问题，不修改用例）</p>
 *
 * <p>与其他核心组件的关系：
 * <ul>
 *   <li>质量检查发生在回归运行前，用来减少“用例配置不合理”导致的无效失败；</li>
 *   <li>第一版全部使用确定性规则，不调用模型，保证检查结果稳定；</li>
 *   <li>检查器只返回问题，不修改用例；修复动作仍交给管理员确认。</li>
 * </ul></p>
 *
 * <p>设计说明：
 * 1. 质量检查发生在回归运行前，用来减少“用例配置不合理”导致的无效失败。
 * 2. 第一版全部使用确定性规则，不调用模型，保证检查结果稳定。
 * 3. 检查器只返回问题，不修改用例；修复动作仍交给管理员确认。</p>
 *
 * 作者: hfj
 */
@Component
public class AgentEvalCaseQualityChecker {

    private static final String HIGH = "HIGH";
    private static final String MEDIUM = "MEDIUM";
    private static final String LOW = "LOW";
    private static final String FIX_SET_MIN_ANSWER_SCORE = "SET_MIN_ANSWER_SCORE_70";
    private static final String FIX_ADD_GUARDRAIL_KEYWORDS = "ADD_GUARDRAIL_REJECT_KEYWORDS";
    private static final String FIX_ADD_JSON_KEYWORDS = "ADD_JSON_FIELD_KEYWORDS";

    /**
     * 检查用例质量。
     *
     * 方法步骤:
     * 1. 遍历传入用例，按 evalType 分发到不同规则。
     * 2. 每发现一个问题就记录 issue，不在检查过程中修改用例。
     * 3. 最后汇总问题用例数和不同风险等级数量，供 Admin 页面展示。
     */
    public AgentEvalCaseQualityReportVO check(List<AgentEvalCase> cases) {
        AgentEvalCaseQualityReportVO report = new AgentEvalCaseQualityReportVO();
        report.setTotalCaseCount(cases == null ? 0 : cases.size());
        if (cases == null || cases.isEmpty()) {
            return report;
        }

        Set<Long> problemCaseIds = new HashSet<>();
        for (AgentEvalCase evalCase : cases) {
            int beforeSize = report.getIssues().size();
            checkCase(evalCase, report);
            if (report.getIssues().size() > beforeSize && evalCase.getId() != null) {
                problemCaseIds.add(evalCase.getId());
            }
        }

        report.setProblemCaseCount(problemCaseIds.size());
        report.setHighRiskIssueCount(countByRisk(report, HIGH));
        report.setMediumRiskIssueCount(countByRisk(report, MEDIUM));
        report.setLowRiskIssueCount(countByRisk(report, LOW));
        return report;
    }

    private void checkCase(AgentEvalCase evalCase, AgentEvalCaseQualityReportVO report) {
        if (evalCase == null) {
            return;
        }
        String evalType = defaultText(evalCase.getEvalType(), "END_TO_END");

        // 1. 通用检查: 用户输入为空时，用例无法真实驱动 Agent 链路。
        if (!StringUtils.hasText(evalCase.getInputMessage())) {
            addIssue(report, evalCase, HIGH, "INPUT_MESSAGE_MISSING", "用户输入为空，运行时无法模拟真实对话。", "补充一条贴近真实用户说法的输入。");
        }

        // 2. 按类型检查核心断言是否齐全。
        if ("TOOL_CALL".equals(evalType)) {
            checkToolCase(evalCase, report);
        } else if ("RAG_RETRIEVAL".equals(evalType)) {
            checkRagCase(evalCase, report);
        } else if ("ANSWER_QUALITY".equals(evalType)) {
            checkAnswerCase(evalCase, report);
        } else if ("GUARDRAIL".equals(evalType)) {
            checkGuardrailCase(evalCase, report);
        } else if ("JSON_OUTPUT".equals(evalType)) {
            checkJsonCase(evalCase, report);
        } else if ("END_TO_END".equals(evalType)) {
            checkEndToEndCase(evalCase, report);
        }
    }

    private void checkToolCase(AgentEvalCase evalCase, AgentEvalCaseQualityReportVO report) {
        if (!StringUtils.hasText(evalCase.getExpectedToolName())) {
            addIssue(report, evalCase, HIGH, "TOOL_EXPECTATION_MISSING", "工具调用用例没有填写期望工具，无法判断工具选择是否正确。", "填写 expectedToolName，或把用例类型改成 ANSWER_QUALITY。");
        }
        if (isInputTooGeneric(evalCase.getInputMessage())) {
            addIssue(report, evalCase, MEDIUM, "TOOL_INPUT_TOO_GENERIC", "用户输入较泛，模型可能把它当普通问答而不调用工具。", "把输入改得更明确，例如加入“分析我的简历”“匹配岗位”“搜索岗位”等触发词。");
        }
    }

    private void checkRagCase(AgentEvalCase evalCase, AgentEvalCaseQualityReportVO report) {
        boolean hasRagTarget = evalCase.getExpectedRagDocumentId() != null
                || evalCase.getExpectedRagChunkId() != null
                || StringUtils.hasText(evalCase.getExpectedRagKeywords());
        if (!hasRagTarget) {
            addIssue(report, evalCase, HIGH, "RAG_EXPECTATION_MISSING", "RAG 用例没有配置文档ID、切片ID或关键词，无法判断召回是否命中。", "填写 expectedRagKeywords，或者指定 expectedRagDocumentId / expectedRagChunkId。");
        }
    }

    private void checkAnswerCase(AgentEvalCase evalCase, AgentEvalCaseQualityReportVO report) {
        if (!StringUtils.hasText(evalCase.getExpectedAnswerKeywords()) && evalCase.getMinAnswerScore() == null) {
            addIssue(
                    report,
                    evalCase,
                    MEDIUM,
                    "ANSWER_ASSERTION_MISSING",
                    "回答质量用例没有答案关键词或最低分，运行后很难判断回答是否达标。",
                    "填写 expectedAnswerKeywords 或 minAnswerScore。",
                    FIX_SET_MIN_ANSWER_SCORE,
                    "设置最低分 70",
                    "确认把该用例的最低回答质量分设置为 70 吗？"
            );
        }
    }

    private void checkGuardrailCase(AgentEvalCase evalCase, AgentEvalCaseQualityReportVO report) {
        String keywords = defaultText(evalCase.getExpectedAnswerKeywords(), "");
        if (!containsAny(keywords, List.of("不能", "无法", "安全", "拒绝", "不可以"))) {
            addIssue(
                    report,
                    evalCase,
                    MEDIUM,
                    "GUARDRAIL_REJECT_KEYWORD_MISSING",
                    "Guardrails 用例缺少拒答关键词，可能无法判断是否安全拒绝。",
                    "答案关键词建议包含“不能、无法、安全、拒绝、不可以”等拒答词。",
                    FIX_ADD_GUARDRAIL_KEYWORDS,
                    "补拒答关键词",
                    "确认给该 Guardrails 用例补充拒答关键词吗？"
            );
        }
    }

    private void checkJsonCase(AgentEvalCase evalCase, AgentEvalCaseQualityReportVO report) {
        String text = defaultText(evalCase.getInputMessage(), "") + "," + defaultText(evalCase.getExpectedAnswerKeywords(), "");
        if (!containsAny(text, List.of("title", "summary", "JSON", "json"))) {
            addIssue(
                    report,
                    evalCase,
                    MEDIUM,
                    "JSON_FIELD_KEYWORD_MISSING",
                    "JSON 输出用例没有明确字段或 JSON 关键词，可能无法判断格式是否正确。",
                    "在输入或答案关键词中加入 title、summary、JSON 等字段要求。",
                    FIX_ADD_JSON_KEYWORDS,
                    "补 JSON 字段关键词",
                    "确认给该 JSON 输出用例补充 JSON、title、summary 关键词吗？"
            );
        }
    }

    private void checkEndToEndCase(AgentEvalCase evalCase, AgentEvalCaseQualityReportVO report) {
        if (StringUtils.hasText(evalCase.getExpectedToolName()) && isInputTooGeneric(evalCase.getInputMessage())) {
            addIssue(report, evalCase, LOW, "END_TO_END_TOOL_INPUT_WEAK", "端到端用例强制期望工具，但输入较泛，可能造成工具选择误判。", "把输入写得更像真实工具场景，或改成 TOOL_CALL 专项用例。");
        }
    }

    private void addIssue(
            AgentEvalCaseQualityReportVO report,
            AgentEvalCase evalCase,
            String riskLevel,
            String issueType,
            String issueMessage,
            String suggestion
    ) {
        addIssue(report, evalCase, riskLevel, issueType, issueMessage, suggestion, null, null, null);
    }

    private void addIssue(
            AgentEvalCaseQualityReportVO report,
            AgentEvalCase evalCase,
            String riskLevel,
            String issueType,
            String issueMessage,
            String suggestion,
            String fixActionType,
            String fixButtonText,
            String fixConfirmText
    ) {
        AgentEvalCaseQualityReportVO.IssueItem item = new AgentEvalCaseQualityReportVO.IssueItem();
        item.setCaseId(evalCase.getId());
        item.setCaseName(evalCase.getCaseName());
        item.setEvalType(defaultText(evalCase.getEvalType(), "END_TO_END"));
        item.setRiskLevel(riskLevel);
        item.setIssueType(issueType);
        item.setIssueMessage(issueMessage);
        item.setSuggestion(suggestion);
        item.setFixable(StringUtils.hasText(fixActionType));
        item.setFixActionType(fixActionType);
        item.setFixButtonText(fixButtonText);
        item.setFixConfirmText(fixConfirmText);
        report.getIssues().add(item);
    }

    private int countByRisk(AgentEvalCaseQualityReportVO report, String riskLevel) {
        return (int) report.getIssues().stream()
                .filter(item -> riskLevel.equals(item.getRiskLevel()))
                .count();
    }

    private boolean isInputTooGeneric(String input) {
        if (!StringUtils.hasText(input)) {
            return true;
        }
        String text = input.trim();
        return text.length() < 10 || containsAny(text, List.of("看看", "帮我", "分析一下"));
    }

    private boolean containsAny(String text, List<String> words) {
        if (!StringUtils.hasText(text)) {
            return false;
        }
        return words.stream().anyMatch(text::contains);
    }

    private String defaultText(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value.trim() : defaultValue;
    }
}
