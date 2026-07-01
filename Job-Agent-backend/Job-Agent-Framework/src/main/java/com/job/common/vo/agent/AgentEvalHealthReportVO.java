package com.job.common.vo.agent;

import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 功能:Agent 核心链路质量体检报告。
 *
 * 设计说明:
 * 1. 这个 VO 面向 Admin 体检看板，不直接承载单条 Eval 明细。
 * 2. 它把最近一次回归批次、核心场景覆盖率、关键指标和失败分类聚合到一起。
 * 3. Admin 页面拿到这个对象后，可以快速判断当前 Agent 是工具、RAG、记忆、Guardrails 还是输出格式在退化。
 */
@Data
public class AgentEvalHealthReportVO {

    private Long latestRunId;
    private String latestRunName;
    private Long datasetId;
    private Integer totalCount = 0;
    private Integer passCount = 0;
    private Integer failCount = 0;
    private BigDecimal passRate = BigDecimal.ZERO;
    private BigDecimal toolAccuracy;
    private BigDecimal paramAccuracy;
    private BigDecimal ragHitRate;
    private BigDecimal answerQualityAvg;
    private BigDecimal coreCoverageRate = BigDecimal.ZERO;
    private String weakestMetric;
    private BigDecimal weakestMetricValue;
    private List<String> coveredCoreTypes = new ArrayList<>();
    private List<String> missingCoreTypes = new ArrayList<>();
    private List<MetricItem> metricItems = new ArrayList<>();
    private List<FailureItem> failureItems = new ArrayList<>();
    private List<String> qualitySuggestions = new ArrayList<>();

    /**
     * 单个体检指标。
     *
     * 说明:
     * 1. metricCode 用于前端稳定识别指标。
     * 2. metricName 用于页面展示。
     * 3. metricValue 统一按百分比或分数展示，是否百分比由 percentMetric 标记。
     */
    @Data
    public static class MetricItem {
        private String metricCode;
        private String metricName;
        private BigDecimal metricValue;
        private Boolean percentMetric = true;
        private String status;
        private String description;
    }

    /**
     * 失败分类统计项。
     *
     * 说明:
     * 1. failureType 来自 agent_eval_result.failure_type。
     * 2. count 表示最近一次回归批次中该失败类型出现次数。
     * 3. suggestion 给 Admin 一个直接排查方向。
     */
    @Data
    public static class FailureItem {
        private String failureType;
        private Long count;
        private String suggestion;
    }
}
