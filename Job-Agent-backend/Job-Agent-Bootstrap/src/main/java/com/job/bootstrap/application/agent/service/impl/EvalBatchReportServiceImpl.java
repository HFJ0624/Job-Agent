package com.job.bootstrap.application.agent.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.job.bootstrap.application.agent.service.AgentEvalService;
import com.job.bootstrap.application.agent.service.EvalBatchReportService;
import com.job.bootstrap.application.agent.service.quality.EvalFailureClassifier;
import com.job.bootstrap.application.agent.service.quality.EvalGateEvaluator;
import com.job.bootstrap.application.agent.service.quality.EvalMetricsCalculator;
import com.job.common.dto.agent.AgentEvalCaseQueryDTO;
import com.job.common.dto.agent.AgentEvalResultQueryDTO;
import com.job.common.dto.agent.AgentEvalRunQueryDTO;
import com.job.common.vo.agent.AgentEvalResultVO;
import com.job.common.vo.agent.AgentEvalRunVO;
import com.job.common.vo.agent.EvalBatchReportVO;
import com.job.common.vo.agent.EvalFailureCategoryVO;
import com.job.common.vo.agent.EvalMetricsVO;
import com.job.common.vo.agent.EvalOverviewVO;
import com.job.common.vo.agent.EvalTrendPointVO;
import com.job.exception.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 功能: 评测批次聚合实现（概览 + 单批报告）。
 *
 * <p>指标一律以 run 的聚合字段为准；失败分类按逐用例结果归因后统计。</p>
 */
@Service
@RequiredArgsConstructor
public class EvalBatchReportServiceImpl implements EvalBatchReportService {

    private static final int TREND_SIZE = 8;
    private static final long CASE_PAGE_SIZE = 500L;

    private final AgentEvalService agentEvalService;
    private final EvalFailureClassifier failureClassifier;

    @Override
    public EvalOverviewVO overview(Long datasetId) {
        EvalOverviewVO overview = new EvalOverviewVO();
        overview.setDatasetId(datasetId);
        overview.setTotalCases(countCases(datasetId));

        AgentEvalRunQueryDTO query = new AgentEvalRunQueryDTO();
        query.setDatasetId(datasetId);
        query.setPageNum(1L);
        query.setPageSize((long) TREND_SIZE);
        IPage<AgentEvalRunVO> page = agentEvalService.pageRuns(query);
        List<AgentEvalRunVO> runs = new ArrayList<>(page.getRecords());

        if (!runs.isEmpty()) {
            AgentEvalRunVO latest = runs.get(0);
            overview.setLatestRun(latest);
            overview.setPassRate(passRate(latest));
            overview.setFailCount(latest.getFailCount());
            overview.setLastRunTime(latest.getEndTime() != null ? latest.getEndTime() : latest.getStartTime());
            overview.setFailureTop(topFailures(latest.getId()));
        } else {
            overview.setTrend(List.of());
            overview.setFailureTop(List.of());
        }

        // 列表通常是倒序，趋势图按时间正序
        Collections.reverse(runs);
        List<EvalTrendPointVO> trend = new ArrayList<>();
        for (AgentEvalRunVO run : runs) {
            EvalTrendPointVO point = new EvalTrendPointVO();
            point.setRunId(run.getId());
            point.setTime(run.getStartTime() != null ? run.getStartTime() : run.getCreateTime());
            point.setPassRate(passRate(run));
            trend.add(point);
        }
        overview.setTrend(trend);
        return overview;
    }

    @Override
    public EvalBatchReportVO report(Long runId) {
        AgentEvalRunVO run = agentEvalService.getRun(runId);
        if (run == null) {
            throw new BizException("评测批次不存在或已被删除");
        }
        List<AgentEvalResultVO> cases = loadResults(runId);

        EvalBatchReportVO report = new EvalBatchReportVO();
        report.setRun(run);
        report.setCases(cases);
        report.setCaseTotal(cases.size());
        report.setFailureCategories(countByCategory(cases));
        attachMetrics(report, cases);
        attachGate(report, run);
        return report;
    }

    /**
     * 挂载"由逐用例重算"的指标；练习 2 未实现时返回 null，前端回退到 run 聚合值。
     */
    private void attachMetrics(EvalBatchReportVO report, List<AgentEvalResultVO> cases) {
        EvalMetricsCalculator.Metrics metrics = EvalMetricsCalculator.calculate(cases);
        if (metrics == null) {
            return;
        }
        EvalMetricsVO vo = new EvalMetricsVO();
        vo.setPassRate(metrics.passRate());
        vo.setToolAccuracy(metrics.toolAccuracy());
        vo.setParamAccuracy(metrics.paramAccuracy());
        vo.setRagHitRate(metrics.ragHitRate());
        vo.setAnswerRate(metrics.answerRate());
        vo.setAvgCostTime(metrics.avgCostTime());
        report.setMetrics(vo);
    }

    /**
     * 挂载门禁结论；练习 3 未实现时返回 null，界面不展示 PASS/BLOCK。
     */
    private void attachGate(EvalBatchReportVO report, AgentEvalRunVO run) {
        AgentEvalRunVO baseline = null;
        if (run.getCompareRunId() != null) {
            try {
                baseline = agentEvalService.getRun(run.getCompareRunId());
            } catch (Exception ignored) {
                // 基线被删不影响报告主体
            }
        }
        EvalGateEvaluator.GateResult gate = EvalGateEvaluator.evaluate(run, baseline);
        if (gate == null) {
            return;
        }
        report.setGatePass(gate.pass());
        report.setGateReason(gate.reason());
        report.setPassRateDelta(gate.passRateDelta());
        report.setToolDelta(gate.toolDelta());
        report.setParamDelta(gate.paramDelta());
    }

    /** 取最近批次的结果并统计失败 Top（不含"通过"）。 */
    private List<EvalFailureCategoryVO> topFailures(Long runId) {
        List<EvalFailureCategoryVO> categories = countByCategory(loadResults(runId));
        return categories.stream().limit(4).toList();
    }

    /** 逐条归因后聚合；按数量倒序，占比按"失败总数"计算。 */
    private List<EvalFailureCategoryVO> countByCategory(List<AgentEvalResultVO> cases) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (AgentEvalResultVO result : cases) {
            String code = failureClassifier.classify(result);
            if (EvalFailureClassifier.NONE.equals(code)) {
                continue;
            }
            counts.merge(code, 1, Integer::sum);
        }
        int failTotal = counts.values().stream().mapToInt(Integer::intValue).sum();
        List<EvalFailureCategoryVO> categories = new ArrayList<>();
        counts.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                .forEach(entry -> {
                    EvalFailureCategoryVO category = new EvalFailureCategoryVO();
                    category.setCode(entry.getKey());
                    category.setLabel(EvalFailureClassifier.labelOf(entry.getKey()));
                    category.setCount(entry.getValue());
                    category.setRatio(percent(entry.getValue(), failTotal));
                    categories.add(category);
                });
        return categories;
    }

    private List<AgentEvalResultVO> loadResults(Long runId) {
        AgentEvalResultQueryDTO query = new AgentEvalResultQueryDTO();
        query.setRunId(runId);
        query.setPageNum(1L);
        query.setPageSize(CASE_PAGE_SIZE);
        IPage<AgentEvalResultVO> page = agentEvalService.pageResults(query);
        return page.getRecords();
    }

    private Integer countCases(Long datasetId) {
        AgentEvalCaseQueryDTO query = new AgentEvalCaseQueryDTO();
        query.setDatasetId(datasetId);
        query.setPageNum(1L);
        query.setPageSize(1L);
        return (int) agentEvalService.pageCases(query).getTotal();
    }

    /** 通过率 = 通过数 / 总数，保留 2 位小数。 */
    private BigDecimal passRate(AgentEvalRunVO run) {
        return percent(run.getPassCount(), run.getTotalCount());
    }

    private BigDecimal percent(Integer numerator, Integer denominator) {
        if (numerator == null || denominator == null || denominator == 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(numerator)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(denominator), 2, RoundingMode.HALF_UP);
    }
}
