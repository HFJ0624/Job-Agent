package com.job.bootstrap.service.impl;

import com.job.common.entity.agent.AgentEvalCase;
import com.job.common.entity.agent.AgentEvalResult;
import com.job.common.entity.agent.AgentEvalRun;
import com.job.common.vo.agent.AgentEvalHealthReportVO;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Agent Eval 质量体检报告构建器，负责把 Eval 数据聚合为可指导行动的质量报告。
 *
 * <p>核心职责：
 * 接收 Service 层查询的最近批次、启用用例和批次结果，进行纯规则统计并生成质量体检报告。
 * 报告包含运行总览、核心链路覆盖率、指标明细、失败分类、最薄弱指标和质量建议。
 * 构建器只做纯规则统计，不访问数据库，不调用模型，方便单元测试稳定回归。</p>
 *
 * <p>所属业务模块：Job-Agent-Bootstrap 模块下的 Agent Eval 子模块（质量报告构建层）。</p>
 *
 * <p>主要调用链：
 * 后台 -> AgentEvalService.buildHealthReport
 * -> AgentEvalHealthReportBuilder.build
 * -> fillRunOverview（运行总览）
 * -> fillCoreCoverage（核心链路覆盖率）
 * -> fillMetrics（指标明细）
 * -> fillFailureItems（失败分类）
 * -> fillWeakestMetric（最薄弱指标）
 * -> fillQualitySuggestions（质量建议）</p>
 *
 * <p>与其他核心组件的关系：
 * <ul>
 *   <li>构建器只做纯规则统计，不访问数据库，不调用模型，方便单元测试稳定回归；</li>
 *   <li>Service 层负责查询最近批次、启用用例和批次结果，然后把数据交给这里聚合；</li>
 *   <li>第一版聚焦核心 Agent 链路：工具选择、RAG 命中、记忆召回、Guardrails、JSON 输出。</li>
 * </ul></p>
 *
 * <p>设计说明：
 * 1. 构建器只做纯规则统计，不访问数据库，不调用模型，方便单元测试稳定回归。
 * 2. Service 层负责查询最近批次、启用用例和批次结果，然后把数据交给这里聚合。
 * 3. 第一版聚焦核心 Agent 链路: 工具选择、RAG 命中、记忆召回、Guardrails、JSON 输出。</p>
 *
 * 作者: hfj
 */
@Component
public class AgentEvalHealthReportBuilder {

    private static final List<String> CORE_TYPES = List.of(
            "TOOL_CALL",
            "RAG_RETRIEVAL",
            "MEMORY_RECALL",
            "GUARDRAIL",
            "JSON_OUTPUT"
    );

    /**
     * 构建质量体检报告。
     *
     * 方法步骤:
     * 1. 先填充最近一次运行批次的总览指标，用来回答“这次回归整体是否健康”。
     * 2. 再统计启用用例覆盖了哪些核心链路，用来回答“测试集是否覆盖完整”。
     * 3. 然后根据最近批次结果统计失败分类，用来回答“主要失败集中在哪里”。
     * 4. 最后找出最薄弱指标并生成建议，让 Admin 能直接知道下一步排查方向。
     */
    public AgentEvalHealthReportVO build(
            AgentEvalRun latestRun,
            List<AgentEvalCase> enabledCases,
            List<AgentEvalResult> latestResults
    ) {
        AgentEvalHealthReportVO report = new AgentEvalHealthReportVO();
        fillRunOverview(report, latestRun);
        fillCoreCoverage(report, enabledCases);
        fillMetrics(report, latestRun);
        fillFailureItems(report, latestResults);
        fillWeakestMetric(report);
        fillQualitySuggestions(report);
        return report;
    }

    private void fillRunOverview(AgentEvalHealthReportVO report, AgentEvalRun latestRun) {
        if (latestRun == null) {
            return;
        }

        // 1. 最近批次是体检报告的主视角，后续结果明细默认围绕这个批次展开。
        report.setLatestRunId(latestRun.getId());
        report.setLatestRunName(latestRun.getRunName());
        report.setDatasetId(latestRun.getDatasetId());
        report.setTotalCount(defaultInt(latestRun.getTotalCount()));
        report.setPassCount(defaultInt(latestRun.getPassCount()));
        report.setFailCount(defaultInt(latestRun.getFailCount()));
        report.setPassRate(calcRate(report.getPassCount(), report.getTotalCount()));
        report.setToolAccuracy(latestRun.getToolAccuracy());
        report.setParamAccuracy(latestRun.getParamAccuracy());
        report.setRagHitRate(latestRun.getRagHitRate());
        report.setAnswerQualityAvg(latestRun.getAnswerQualityAvg());
    }

    private void fillCoreCoverage(AgentEvalHealthReportVO report, List<AgentEvalCase> enabledCases) {
        // 1. 用启用用例的 evalType 计算覆盖率，禁用用例不进入质量保障范围。
        Set<String> actualTypes = nullToEmpty(enabledCases).stream()
                .map(AgentEvalCase::getEvalType)
                .filter(StringUtils::hasText)
                .map(String::trim)
                .collect(Collectors.toSet());

        // 2. 只统计核心链路类型，其他专项用例可以存在，但不影响核心覆盖率。
        List<String> covered = CORE_TYPES.stream()
                .filter(actualTypes::contains)
                .toList();
        List<String> missing = CORE_TYPES.stream()
                .filter(type -> !actualTypes.contains(type))
                .toList();

        report.setCoveredCoreTypes(covered);
        report.setMissingCoreTypes(missing);
        report.setCoreCoverageRate(calcRate(covered.size(), CORE_TYPES.size()));
    }

    private void fillMetrics(AgentEvalHealthReportVO report, AgentEvalRun latestRun) {
        List<AgentEvalHealthReportVO.MetricItem> items = new ArrayList<>();

        // 1. 通过率表示端到端整体稳定性。
        items.add(metric("PASS_RATE", "整体通过率", report.getPassRate(), true, "最近批次通过用例数 / 总用例数"));

        // 2. 覆盖率表示测试集是否覆盖核心链路，避免只测某一类能力。
        items.add(metric("CORE_COVERAGE", "核心链路覆盖率", report.getCoreCoverageRate(), true, "工具、RAG、记忆、Guardrails、JSON 输出五类覆盖情况"));

        // 3. 以下指标来自 Eval 运行批次，空值说明最近批次没有对应断言。
        if (latestRun != null) {
            items.add(metric("TOOL_ACCURACY", "工具选择准确率", latestRun.getToolAccuracy(), true, "期望工具是否被正确调用"));
            items.add(metric("PARAM_ACCURACY", "参数准确率", latestRun.getParamAccuracy(), true, "实际工具参数是否包含期望参数"));
            items.add(metric("RAG_HIT_RATE", "RAG 命中率", latestRun.getRagHitRate(), true, "期望文档、切片或关键词是否被召回"));
            items.add(metric("ANSWER_QUALITY", "回答质量均分", latestRun.getAnswerQualityAvg(), false, "规则分和 Judge 分综合后的回答质量"));
        }

        report.setMetricItems(items);
    }

    private void fillFailureItems(AgentEvalHealthReportVO report, List<AgentEvalResult> latestResults) {
        // 1. 失败分类来自单条结果 failureType，先过滤空值，避免通过用例污染统计。
        Map<String, Long> stats = nullToEmpty(latestResults).stream()
                .map(AgentEvalResult::getFailureType)
                .filter(StringUtils::hasText)
                .collect(Collectors.groupingBy(Function.identity(), LinkedHashMap::new, Collectors.counting()));

        // 2. 按失败次数倒序，Admin 页面优先展示最需要处理的问题。
        List<AgentEvalHealthReportVO.FailureItem> items = stats.entrySet().stream()
                .map(entry -> failureItem(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparing(AgentEvalHealthReportVO.FailureItem::getCount).reversed())
                .toList();
        report.setFailureItems(items);
    }

    private void fillWeakestMetric(AgentEvalHealthReportVO report) {
        // 1. 只从有值的百分比指标里找最薄弱项，回答质量分不是百分比，不参与这个比较。
        AgentEvalHealthReportVO.MetricItem weakest = report.getMetricItems().stream()
                .filter(item -> Boolean.TRUE.equals(item.getPercentMetric()))
                .filter(item -> item.getMetricValue() != null)
                .min(Comparator.comparing(AgentEvalHealthReportVO.MetricItem::getMetricValue))
                .orElse(null);
        if (weakest == null) {
            return;
        }
        report.setWeakestMetric(weakest.getMetricCode());
        report.setWeakestMetricValue(weakest.getMetricValue());
    }

    private void fillQualitySuggestions(AgentEvalHealthReportVO report) {
        List<String> suggestions = new ArrayList<>();

        // 1. 覆盖率不满时，优先建议补用例，而不是盲目改 Prompt 或模型。
        if (!report.getMissingCoreTypes().isEmpty()) {
            suggestions.add("核心链路测试集还缺少 " + String.join("、", report.getMissingCoreTypes()) + " 用例，建议先补齐覆盖再判断质量。");
        }

        // 2. 根据最薄弱指标给出排查方向，让质量报告不仅展示数字，也能指导动作。
        if (Objects.equals(report.getWeakestMetric(), "RAG_HIT_RATE")) {
            suggestions.add("RAG 命中率最低，建议检查知识切片质量、召回关键词、权限过滤和重排序规则。");
        } else if (Objects.equals(report.getWeakestMetric(), "TOOL_ACCURACY")) {
            suggestions.add("工具选择准确率最低，建议检查 Planner 工具选择提示词和 Tool Schema 描述。");
        } else if (Objects.equals(report.getWeakestMetric(), "PARAM_ACCURACY")) {
            suggestions.add("参数准确率最低，建议检查参数抽取、名称映射和工具入参校验。");
        } else if (Objects.equals(report.getWeakestMetric(), "PASS_RATE")) {
            suggestions.add("整体通过率最低，建议打开失败结果列表，优先处理失败次数最多的分类。");
        }

        // 3. 没有失败但没有最近批次时，提醒先运行回归，避免空报告被误认为健康。
        if (report.getLatestRunId() == null) {
            suggestions.add("暂无最近回归批次，请先运行核心链路数据集，生成可对比的质量基线。");
        }

        report.setQualitySuggestions(suggestions);
    }

    private AgentEvalHealthReportVO.MetricItem metric(
            String code,
            String name,
            BigDecimal value,
            boolean percentMetric,
            String description
    ) {
        AgentEvalHealthReportVO.MetricItem item = new AgentEvalHealthReportVO.MetricItem();
        item.setMetricCode(code);
        item.setMetricName(name);
        item.setMetricValue(value);
        item.setPercentMetric(percentMetric);
        item.setDescription(description);
        item.setStatus(resolveMetricStatus(value, percentMetric));
        return item;
    }

    private AgentEvalHealthReportVO.FailureItem failureItem(String failureType, Long count) {
        AgentEvalHealthReportVO.FailureItem item = new AgentEvalHealthReportVO.FailureItem();
        item.setFailureType(failureType);
        item.setCount(count);
        item.setSuggestion(resolveFailureSuggestion(failureType));
        return item;
    }

    private String resolveMetricStatus(BigDecimal value, boolean percentMetric) {
        if (value == null) {
            return "NO_DATA";
        }
        if (!percentMetric) {
            return value.compareTo(BigDecimal.valueOf(80)) >= 0 ? "GOOD" : "WARN";
        }
        if (value.compareTo(BigDecimal.valueOf(90)) >= 0) {
            return "GOOD";
        }
        if (value.compareTo(BigDecimal.valueOf(70)) >= 0) {
            return "WARN";
        }
        return "RISK";
    }

    private String resolveFailureSuggestion(String failureType) {
        if (!StringUtils.hasText(failureType)) {
            return "查看单条失败结果，确认是否为执行异常或断言缺失。";
        }
        if (failureType.contains("TOOL")) {
            return "优先检查 Planner 工具选择、Tool Schema 描述和工具权限配置。";
        }
        if (failureType.contains("RAG")) {
            return "优先检查知识切片、召回关键词、混合检索和权限过滤。";
        }
        if (failureType.contains("GUARDRAIL")) {
            return "优先检查 Guardrails 规则是否误拦截或漏拦截。";
        }
        if (failureType.contains("JSON")) {
            return "优先检查 Prompt 输出格式约束和 JSON 校验规则。";
        }
        if (failureType.contains("EXECUTION")) {
            return "优先检查模型路由、工具执行异常和 Trace 日志。";
        }
        return "查看失败明细和 Trace，定位对应链路。";
    }

    private BigDecimal calcRate(int pass, int total) {
        if (total <= 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(pass * 100.0 / total).setScale(2, RoundingMode.HALF_UP);
    }

    private int defaultInt(Integer value) {
        return value == null ? 0 : value;
    }

    private <T> List<T> nullToEmpty(List<T> values) {
        return values == null ? List.of() : values;
    }
}
