package com.job.bootstrap.controller.admin;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.job.bootstrap.service.AgentEvalService;
import com.job.common.dto.agent.AgentEvalCaseQueryDTO;
import com.job.common.dto.agent.AgentEvalCaseSaveDTO;
import com.job.common.dto.agent.AgentEvalDatasetQueryDTO;
import com.job.common.dto.agent.AgentEvalDatasetSaveDTO;
import com.job.common.dto.agent.AgentEvalResultQueryDTO;
import com.job.common.dto.agent.AgentEvalRunQueryDTO;
import com.job.common.entity.base.Result;
import com.job.common.entity.base.ResultCodeEnum;
import com.job.common.vo.agent.AgentEvalCaseVO;
import com.job.common.vo.agent.AgentEvalDatasetVO;
import com.job.common.vo.agent.AgentEvalResultVO;
import com.job.common.vo.agent.AgentEvalRunVO;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 作者:hfj
 * 功能:Agent Eval 平台管理接口
 * 日期:2026/6/24
 */
@Tag(name = "Agent Eval 平台")
@RestController
@RequestMapping("/admin/agent/eval")
@RequiredArgsConstructor
public class AdminAgentEvalController {

    private final AgentEvalService agentEvalService;

    @GetMapping("/datasets/page")
    public Result<IPage<AgentEvalDatasetVO>> pageDatasets(AgentEvalDatasetQueryDTO query) {
        return Result.build(agentEvalService.pageDatasets(query), ResultCodeEnum.SUCCESS);
    }

    @GetMapping("/datasets/enabled")
    public Result<List<AgentEvalDatasetVO>> listEnabledDatasets() {
        return Result.build(agentEvalService.listEnabledDatasets(), ResultCodeEnum.SUCCESS);
    }

    @PostMapping("/datasets")
    public Result<AgentEvalDatasetVO> createDataset(@RequestBody AgentEvalDatasetSaveDTO request) {
        return Result.build(agentEvalService.saveDataset(null, request), ResultCodeEnum.SUCCESS);
    }

    @PutMapping("/datasets/{id}")
    public Result<AgentEvalDatasetVO> updateDataset(@PathVariable Long id,
                                                    @RequestBody AgentEvalDatasetSaveDTO request) {
        return Result.build(agentEvalService.saveDataset(id, request), ResultCodeEnum.SUCCESS);
    }

    @DeleteMapping("/datasets/{id}")
    public Result<Void> deleteDataset(@PathVariable Long id) {
        agentEvalService.deleteDataset(id);
        return Result.build(null, ResultCodeEnum.SUCCESS);
    }

    @GetMapping("/cases/page")
    public Result<IPage<AgentEvalCaseVO>> pageCases(AgentEvalCaseQueryDTO query) {
        return Result.build(agentEvalService.pageCases(query), ResultCodeEnum.SUCCESS);
    }

    @PostMapping("/cases")
    public Result<AgentEvalCaseVO> createCase(@RequestBody AgentEvalCaseSaveDTO request) {
        return Result.build(agentEvalService.saveCase(null, request), ResultCodeEnum.SUCCESS);
    }

    @PutMapping("/cases/{id}")
    public Result<AgentEvalCaseVO> updateCase(@PathVariable Long id,
                                              @RequestBody AgentEvalCaseSaveDTO request) {
        return Result.build(agentEvalService.saveCase(id, request), ResultCodeEnum.SUCCESS);
    }

    @DeleteMapping("/cases/{id}")
    public Result<Void> deleteCase(@PathVariable Long id) {
        agentEvalService.deleteCase(id);
        return Result.build(null, ResultCodeEnum.SUCCESS);
    }

    @PostMapping("/run/{caseId}")
    public Result<AgentEvalRunVO> runCase(@PathVariable Long caseId) {
        return Result.build(agentEvalService.runCase(caseId), ResultCodeEnum.SUCCESS);
    }

    @PostMapping("/run-dataset/{datasetId}")
    public Result<AgentEvalRunVO> runDataset(@PathVariable Long datasetId) {
        return Result.build(agentEvalService.runDataset(datasetId), ResultCodeEnum.SUCCESS);
    }

    @PostMapping("/run-all")
    public Result<Integer> runAll() {
        return Result.build(agentEvalService.runAllEnabledCases(), ResultCodeEnum.SUCCESS);
    }

    @PostMapping("/runs/all")
    public Result<AgentEvalRunVO> runAllWithDetail() {
        return Result.build(agentEvalService.runAll(), ResultCodeEnum.SUCCESS);
    }

    @PostMapping("/runs/{runId}/baseline")
    public Result<AgentEvalRunVO> setBaseline(@PathVariable Long runId) {
        return Result.build(agentEvalService.setBaseline(runId), ResultCodeEnum.SUCCESS);
    }

    @GetMapping("/runs/page")
    public Result<IPage<AgentEvalRunVO>> pageRuns(AgentEvalRunQueryDTO query) {
        return Result.build(agentEvalService.pageRuns(query), ResultCodeEnum.SUCCESS);
    }

    @GetMapping("/results/page")
    public Result<IPage<AgentEvalResultVO>> pageResults(AgentEvalResultQueryDTO query) {
        return Result.build(agentEvalService.pageResults(query), ResultCodeEnum.SUCCESS);
    }
}
