package com.job.bootstrap.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.job.common.dto.agent.AgentEvalCaseQueryDTO;
import com.job.common.dto.agent.AgentEvalCaseSaveDTO;
import com.job.common.dto.agent.AgentEvalDatasetQueryDTO;
import com.job.common.dto.agent.AgentEvalDatasetSaveDTO;
import com.job.common.dto.agent.AgentEvalResultQueryDTO;
import com.job.common.dto.agent.AgentEvalRunQueryDTO;
import com.job.common.vo.agent.AgentEvalCaseVO;
import com.job.common.vo.agent.AgentEvalDatasetVO;
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

    void deleteCase(Long id);

    AgentEvalRunVO runCase(Long caseId);

    AgentEvalRunVO runDataset(Long datasetId);

    AgentEvalRunVO runAll();

    AgentEvalRunVO setBaseline(Long runId);

    IPage<AgentEvalRunVO> pageRuns(AgentEvalRunQueryDTO query);

    IPage<AgentEvalResultVO> pageResults(AgentEvalResultQueryDTO query);

    List<AgentEvalDatasetVO> listEnabledDatasets();

    /**
     * 兼容旧接口：运行所有启用用例并返回通过数量。
     *
     * @return 通过数量
     */
    Integer runAllEnabledCases();
}
