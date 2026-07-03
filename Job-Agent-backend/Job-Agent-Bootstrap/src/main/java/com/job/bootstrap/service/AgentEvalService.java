package com.job.bootstrap.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.job.common.dto.agent.AgentEvalCaseQueryDTO;
import com.job.common.dto.agent.AgentEvalCaseSaveDTO;
import com.job.common.dto.agent.AgentEvalCoreTemplateCreateDTO;
import com.job.common.dto.agent.AgentEvalDatasetQueryDTO;
import com.job.common.dto.agent.AgentEvalDatasetSaveDTO;
import com.job.common.dto.agent.AgentEvalQuickFixDTO;
import com.job.common.dto.agent.AgentEvalResultQueryDTO;
import com.job.common.dto.agent.AgentEvalRunQueryDTO;
import com.job.common.vo.agent.AgentEvalCaseVO;
import com.job.common.vo.agent.AgentEvalCaseQualityReportVO;
import com.job.common.vo.agent.AgentEvalCoreTemplateCreateResultVO;
import com.job.common.vo.agent.AgentEvalDatasetVO;
import com.job.common.vo.agent.AgentEvalHealthReportVO;
import com.job.common.vo.agent.AgentEvalResultDiagnosisVO;
import com.job.common.vo.agent.AgentEvalResultVO;
import com.job.common.vo.agent.AgentEvalRunVO;

import java.util.List;

/**
 * 作者:hfj
 * 功能:Agent Eval 平台服务，负责数据集、用例、批量运行和结果统计
 * 日期:2026/6/24
 */
public interface AgentEvalService {

    IPage<AgentEvalDatasetVO> pageDatasets(AgentEvalDatasetQueryDTO query);

    AgentEvalDatasetVO saveDataset(Long id, AgentEvalDatasetSaveDTO request);

    void deleteDataset(Long id);

    IPage<AgentEvalCaseVO> pageCases(AgentEvalCaseQueryDTO query);

    AgentEvalCaseVO saveCase(Long id, AgentEvalCaseSaveDTO request);

    AgentEvalCoreTemplateCreateResultVO createCoreTemplates(AgentEvalCoreTemplateCreateDTO request);

    void deleteCase(Long id);

    AgentEvalRunVO runCase(Long caseId);

    AgentEvalRunVO runDataset(Long datasetId);

    AgentEvalRunVO runAll();

    AgentEvalRunVO setBaseline(Long runId);

    IPage<AgentEvalRunVO> pageRuns(AgentEvalRunQueryDTO query);

    IPage<AgentEvalResultVO> pageResults(AgentEvalResultQueryDTO query);

    AgentEvalResultDiagnosisVO diagnoseResult(Long resultId);

    AgentEvalCaseVO applyQuickFix(Long resultId, AgentEvalQuickFixDTO request);

    AgentEvalCaseVO applyCaseQualityFix(Long caseId, AgentEvalQuickFixDTO request);

    /**
     * 检查 Eval 用例配置质量。
     *
     * @param datasetId 数据集 ID，为空时检查全部启用用例
     * @return 用例质量检查报告
     */
    AgentEvalCaseQualityReportVO checkCaseQuality(Long datasetId);

    List<AgentEvalDatasetVO> listEnabledDatasets();

    /**
     * 构建 Agent 核心链路质量体检报告。
     *
     * @param datasetId 数据集 ID，为空时使用全量最近一次回归批次
     * @return 核心链路质量体检报告
     */
    AgentEvalHealthReportVO buildHealthReport(Long datasetId);

    /**
     * 兼容旧接口：运行所有启用用例并返回通过数量。
     *
     * @return 通过数量
     */
    Integer runAllEnabledCases();
}
