package com.job.bootstrap.service.eval;

import com.job.bootstrap.service.impl.AgentEvalHealthReportBuilder;
import com.job.common.entity.agent.AgentEvalCase;
import com.job.common.entity.agent.AgentEvalResult;
import com.job.common.entity.agent.AgentEvalRun;
import com.job.common.vo.agent.AgentEvalHealthReportVO;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Agent 质量体检报告构建器测试。
 *
 * 说明:
 * 1. 体检报告是 Eval 平台的“质量视角”，它不直接调用模型，也不依赖数据库。
 * 2. 这里先用纯对象构造输入，保证覆盖率、通过率、失败分类这些规则可以稳定回归。
 * 3. 后续 Controller / Service 只负责查询数据并交给构建器，避免把统计逻辑散落在接口层。
 */
class AgentEvalHealthReportBuilderTest {

    @Test
    void shouldBuildCoreAgentHealthReportFromLatestRunAndCases() {
        AgentEvalHealthReportBuilder builder = new AgentEvalHealthReportBuilder();

        AgentEvalRun latestRun = new AgentEvalRun();
        latestRun.setId(9L);
        latestRun.setRunName("核心链路回归");
        latestRun.setTotalCount(5);
        latestRun.setPassCount(3);
        latestRun.setFailCount(2);
        latestRun.setToolAccuracy(new BigDecimal("80.00"));
        latestRun.setParamAccuracy(new BigDecimal("100.00"));
        latestRun.setRagHitRate(new BigDecimal("50.00"));
        latestRun.setAnswerQualityAvg(new BigDecimal("82.50"));

        List<AgentEvalCase> cases = List.of(
                caseOf("工具选择", "TOOL_CALL"),
                caseOf("RAG 命中", "RAG_RETRIEVAL"),
                caseOf("记忆召回", "MEMORY_RECALL"),
                caseOf("Guardrails", "GUARDRAIL"),
                caseOf("JSON 输出", "JSON_OUTPUT")
        );

        List<AgentEvalResult> results = List.of(
                resultOf("TOOL_CALL", 1, null),
                resultOf("RAG_RETRIEVAL", 0, "RAG_HIT_FAILED"),
                resultOf("MEMORY_RECALL", 1, null),
                resultOf("GUARDRAIL", 0, "GUARDRAIL_MISS"),
                resultOf("JSON_OUTPUT", 1, null)
        );

        AgentEvalHealthReportVO report = builder.build(latestRun, cases, results);

        assertThat(report.getLatestRunId()).isEqualTo(9L);
        assertThat(report.getPassRate()).isEqualByComparingTo("60.00");
        assertThat(report.getCoreCoverageRate()).isEqualByComparingTo("100.00");
        assertThat(report.getCoveredCoreTypes()).containsExactlyInAnyOrder(
                "TOOL_CALL", "RAG_RETRIEVAL", "MEMORY_RECALL", "GUARDRAIL", "JSON_OUTPUT"
        );
        assertThat(report.getMissingCoreTypes()).isEmpty();
        assertThat(report.getWeakestMetric()).isEqualTo("RAG_HIT_RATE");
        assertThat(report.getFailureItems()).hasSize(2);
        assertThat(report.getQualitySuggestions()).anyMatch(text -> text.contains("RAG"));
    }

    private AgentEvalCase caseOf(String name, String evalType) {
        AgentEvalCase evalCase = new AgentEvalCase();
        evalCase.setCaseName(name);
        evalCase.setEvalType(evalType);
        evalCase.setEnableStatus(1);
        return evalCase;
    }

    private AgentEvalResult resultOf(String evalType, int passStatus, String failureType) {
        AgentEvalResult result = new AgentEvalResult();
        result.setEvalType(evalType);
        result.setPassStatus(passStatus);
        result.setFailureType(failureType);
        return result;
    }
}
