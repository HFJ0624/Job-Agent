package com.job.bootstrap.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.job.common.entity.agent.AgentEvalResult;
import com.job.common.vo.agent.AgentEvalResultDiagnosisVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Agent Eval 单条结果规则诊断器，负责解释"为什么失败"和"下一步排查哪里"。
 *
 * <p>核心职责：
 * 根据 Eval 结果字段（failureType、actualTools、ragResultsJson、judgeScore 等）做确定性诊断，
 * 生成包含根因、排查建议和证据的诊断 VO，供后台管理员定位 Agent 链路问题。
 * 诊断重点不是重新评分，而是解释失败原因和排查方向。</p>
 *
 * <p>所属业务模块：Job-Agent-Bootstrap 模块下的 Agent Eval 子模块（结果诊断层）。</p>
 *
 * <p>主要调用链：
 * 后台诊断页面 -> AgentEvalService.diagnoseResult
 * -> AgentEvalResultDiagnosisBuilder.build
 * -> diagnose*（按 failureType 分发诊断规则）
 * -> 返回 AgentEvalResultDiagnosisVO</p>
 *
 * <p>与其他核心组件的关系：
 * <ul>
 *   <li>V1 不调用模型，只根据 Eval 结果字段做确定性诊断；</li>
 *   <li>诊断重点不是重新评分，而是解释“为什么失败”和“下一步排查哪里”；</li>
 *   <li>规则集中放在这里，避免 Controller 和页面里到处写 if/else。</li>
 * </ul></p>
 *
 * <p>设计说明：
 * 1. V1 不调用模型，只根据 Eval 结果字段做确定性诊断。
 * 2. 诊断重点不是重新评分，而是解释“为什么失败”和“下一步排查哪里”。
 * 3. 规则集中放在这里，避免 Controller 和页面里到处写 if/else。</p>
 *
 * 作者: hfj
 */
@Component
@RequiredArgsConstructor
public class AgentEvalResultDiagnosisBuilder {

    private final ObjectMapper objectMapper;

    /**
     * 构建单条 Eval 结果诊断。
     *
     * 方法步骤:
     * 1. 先填充基础信息和证据，确保管理员能看到诊断依据。
     * 2. 如果用例已通过，返回低优先级说明，不再制造额外告警。
     * 3. 如果失败，则按 failureType 分派到具体诊断规则。
     * 4. 最后补充通用建议，避免未知失败类型没有排查方向。
     */
    public AgentEvalResultDiagnosisVO build(AgentEvalResult result) {
        AgentEvalResultDiagnosisVO diagnosis = new AgentEvalResultDiagnosisVO();
        diagnosis.setResultId(result.getId());
        diagnosis.setPassStatus(result.getPassStatus());
        diagnosis.setFailureType(result.getFailureType());
        diagnosis.setPriority(resolvePriority(result));
        fillEvidence(result, diagnosis);

        if (result.getPassStatus() != null && result.getPassStatus() == 1) {
            diagnosis.setSummary("该用例已通过，暂无失败诊断。");
            diagnosis.getSuggestions().add("如果仍然觉得回答质量不稳定，可以查看实际回答、工具调用和 RAG 结果做人工复核。");
            return diagnosis;
        }

        String failureType = result.getFailureType();
        if (!StringUtils.hasText(failureType)) {
            diagnoseUnknownFailure(result, diagnosis);
        } else if (failureType.contains("TOOL_SELECT")) {
            diagnoseToolSelect(result, diagnosis);
        } else if (failureType.contains("TOOL_PARAM")) {
            diagnoseToolParam(result, diagnosis);
        } else if (failureType.contains("RAG")) {
            diagnoseRag(result, diagnosis);
        } else if (failureType.contains("ANSWER_KEYWORD")) {
            diagnoseAnswerKeyword(result, diagnosis);
        } else if (failureType.contains("JSON")) {
            diagnoseJsonOutput(result, diagnosis);
        } else if (failureType.contains("GUARDRAIL")) {
            diagnoseGuardrail(result, diagnosis);
        } else if (failureType.contains("EXECUTION")) {
            diagnoseExecution(result, diagnosis);
        } else {
            diagnoseUnknownFailure(result, diagnosis);
        }

        fillFallbackSuggestion(diagnosis);
        return diagnosis;
    }

    private void diagnoseToolSelect(AgentEvalResult result, AgentEvalResultDiagnosisVO diagnosis) {
        List<String> actualTools = parseStringArray(result.getActualTools());
        diagnosis.setSummary("工具选择失败，Agent 实际调用的工具与期望工具不一致。");
        if (actualTools.isEmpty()) {
            diagnosis.getRootCauses().add("本次对话没有记录到任何工具调用，模型可能把请求当成普通问答处理。");
            diagnosis.getSuggestions().add("检查 Eval 用例的用户输入是否足够明确，例如加入“分析我的简历”“匹配岗位”等触发工具的表达。");
            diagnosis.getSuggestions().add("检查 Planner 提示词和 Tool Schema 描述，确认工具能力名称和触发场景写得足够清楚。");
        } else {
            diagnosis.getRootCauses().add("实际调用了工具，但没有命中期望工具，可能是期望工具名或真实工具名不一致。");
            diagnosis.getSuggestions().add("打开“工具”查看 actualTools，确认期望工具名是否应改成真实工具名或统一后的工具名。");
            diagnosis.getSuggestions().add("如果实际工具业务上也合理，建议调整 Eval 用例的期望工具，避免测试口径过窄。");
        }
        if (StringUtils.hasText(result.getExpectedToolName())) {
            diagnosis.getEvidence().add("期望工具: " + result.getExpectedToolName());
        }
        diagnosis.getEvidence().add("实际工具: " + (actualTools.isEmpty() ? "[]" : actualTools));
    }

    private void diagnoseToolParam(AgentEvalResult result, AgentEvalResultDiagnosisVO diagnosis) {
        diagnosis.setSummary("工具参数失败，Agent 调用了工具，但参数没有包含期望 JSON。");
        diagnosis.getRootCauses().add("参数抽取可能不完整，或者 Eval 期望参数 JSON 写得过严。");
        diagnosis.getSuggestions().add("查看“工具”里的实际参数，对比 expectedToolParamsJson 是否字段名、类型和值都一致。");
        diagnosis.getSuggestions().add("如果只关心部分字段，建议期望 JSON 只填写关键字段，避免完整参数精确匹配导致误判。");
    }

    private void diagnoseRag(AgentEvalResult result, AgentEvalResultDiagnosisVO diagnosis) {
        boolean emptyRag = isJsonArrayEmpty(result.getRagResultsJson());
        diagnosis.setSummary("RAG 命中失败，召回结果没有命中期望文档、切片或关键词。");
        if (emptyRag) {
            diagnosis.getRootCauses().add("RAG 结果为空，可能知识库没有对应数据、向量未入库、权限过滤过严或没有触发 RAG 工具。");
            diagnosis.getSuggestions().add("先到 RAG 知识库确认文档和 chunk 是否存在，并检查 vectorStatus/indexStatus 是否正常。");
            diagnosis.getSuggestions().add("检查当前测试用户是否有权限访问该知识，公共/私有权限过滤可能导致召回为空。");
        } else {
            diagnosis.getRootCauses().add("RAG 有召回结果，但没有命中期望条件，可能关键词过严或 chunk 内容不完整。");
            diagnosis.getSuggestions().add("打开“RAG”查看实际召回内容，适当放宽 expectedRagKeywords 或改用明确的文档ID/切片ID。");
            diagnosis.getSuggestions().add("如果召回内容主题偏移，检查切块质量、混合检索关键词和重排序规则。");
        }
    }

    private void diagnoseAnswerKeyword(AgentEvalResult result, AgentEvalResultDiagnosisVO diagnosis) {
        diagnosis.setSummary("答案关键词失败，最终回答没有包含用例要求的关键词。");
        if (result.getJudgeScore() != null && result.getJudgeScore().compareTo(BigDecimal.valueOf(70)) >= 0) {
            diagnosis.getRootCauses().add("Judge 分不低但关键词失败，可能是答案语义正确但关键词设置太死。");
            diagnosis.getSuggestions().add("建议把 expectedAnswerKeywords 改成更宽泛的业务关键词，或者减少必须同时命中的词。");
        } else {
            diagnosis.getRootCauses().add("回答质量分或 Judge 分偏低，回答方向可能确实没有满足用例目标。");
            diagnosis.getSuggestions().add("查看实际回答和 Judge 原因，确认是 Prompt、RAG 召回还是工具执行导致回答偏题。");
        }
        diagnosis.getEvidence().add("答案关键词命中状态: " + result.getAnswerKeywordPass());
    }

    private void diagnoseJsonOutput(AgentEvalResult result, AgentEvalResultDiagnosisVO diagnosis) {
        diagnosis.setSummary("JSON 输出失败，模型输出可能不是合法 JSON 或缺少要求字段。");
        diagnosis.getRootCauses().add("Prompt 对“只输出 JSON”的约束可能不够强，或模型返回了额外解释文本。");
        diagnosis.getSuggestions().add("检查实际回答是否能被 JSON.parse 解析，是否包含 title、summary 等期望字段。");
        diagnosis.getSuggestions().add("建议在对应 Prompt 版本里明确要求不要 Markdown、不要解释、只输出 JSON 对象。");
    }

    private void diagnoseGuardrail(AgentEvalResult result, AgentEvalResultDiagnosisVO diagnosis) {
        diagnosis.setSummary("Guardrails 用例失败，安全拦截结果不符合预期。");
        diagnosis.getRootCauses().add("可能出现了漏拦截、误拦截，或者拒答文案没有命中 Eval 关键词。");
        diagnosis.getSuggestions().add("查看实际回答，确认是否拒绝了越权请求。若已经拒绝但关键词失败，放宽答案关键词。");
        diagnosis.getSuggestions().add("若没有拒绝，检查 PromptInjectionGuard、ToolAccessGuard 和敏感操作规则。");
    }

    private void diagnoseExecution(AgentEvalResult result, AgentEvalResultDiagnosisVO diagnosis) {
        diagnosis.setSummary("执行异常，用例没有完整跑完 Agent 链路。");
        diagnosis.getRootCauses().add("可能是模型路由、工具调用、RAG 查询或数据库操作异常。");
        diagnosis.getSuggestions().add("优先根据 traceId 查看 Agent Trace，再查看模型调用日志和工具执行错误。");
        diagnosis.getSuggestions().add("如果是外部模型失败，检查模型配置、API Key、超时、重试和熔断状态。");
    }

    private void diagnoseUnknownFailure(AgentEvalResult result, AgentEvalResultDiagnosisVO diagnosis) {
        diagnosis.setSummary("未知失败类型，需要结合失败原因和 Trace 进一步排查。");
        diagnosis.getRootCauses().add("当前 failureType 为空或不是已知分类，可能是新规则未纳入诊断器。");
        diagnosis.getSuggestions().add("查看 failReason、actualAnswer、actualTools、ragResultsJson 和 traceId，确认失败来源。");
    }

    private void fillEvidence(AgentEvalResult result, AgentEvalResultDiagnosisVO diagnosis) {
        if (StringUtils.hasText(result.getFailReason())) {
            diagnosis.getEvidence().add("失败原因: " + limitText(result.getFailReason(), 260));
        }
        if (StringUtils.hasText(result.getJudgeReason())) {
            diagnosis.getEvidence().add("Judge 原因: " + limitText(result.getJudgeReason(), 260));
        }
        if (result.getTraceId() != null) {
            diagnosis.getEvidence().add("Trace ID: " + result.getTraceId());
        }
        if (result.getAnswerQualityScore() != null) {
            diagnosis.getEvidence().add("回答质量分: " + result.getAnswerQualityScore());
        }
        if (result.getJudgeScore() != null) {
            diagnosis.getEvidence().add("Judge 分: " + result.getJudgeScore());
        }
    }

    private void fillFallbackSuggestion(AgentEvalResultDiagnosisVO diagnosis) {
        if (diagnosis.getSuggestions().isEmpty()) {
            diagnosis.getSuggestions().add("打开该结果的回答、工具、RAG 和 Judge 详情，按 Trace 顺序定位失败链路。");
        }
        if (diagnosis.getRootCauses().isEmpty()) {
            diagnosis.getRootCauses().add("暂无明确根因，建议结合失败原因和 Trace 进一步确认。");
        }
    }

    private String resolvePriority(AgentEvalResult result) {
        if (result.getPassStatus() != null && result.getPassStatus() == 1) {
            return "LOW";
        }
        String failureType = result.getFailureType();
        if (!StringUtils.hasText(failureType)) {
            return "MEDIUM";
        }
        if (failureType.contains("EXECUTION") || failureType.contains("GUARDRAIL")) {
            return "HIGH";
        }
        if (failureType.contains("TOOL") || failureType.contains("RAG")) {
            return "MEDIUM";
        }
        return "LOW";
    }

    private List<String> parseStringArray(String json) {
        List<String> values = new ArrayList<>();
        if (!StringUtils.hasText(json)) {
            return values;
        }
        try {
            JsonNode root = objectMapper.readTree(json);
            if (!root.isArray()) {
                return values;
            }
            for (JsonNode item : root) {
                if (item.isTextual() && StringUtils.hasText(item.asText())) {
                    values.add(item.asText());
                }
            }
        } catch (Exception ignored) {
            if (StringUtils.hasText(json)) {
                values.add(json);
            }
        }
        return values;
    }

    private boolean isJsonArrayEmpty(String json) {
        if (!StringUtils.hasText(json)) {
            return true;
        }
        try {
            JsonNode root = objectMapper.readTree(json);
            return root.isArray() && root.isEmpty();
        } catch (Exception ignored) {
            return false;
        }
    }

    private String limitText(String text, int maxLength) {
        if (!StringUtils.hasText(text) || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...";
    }
}
