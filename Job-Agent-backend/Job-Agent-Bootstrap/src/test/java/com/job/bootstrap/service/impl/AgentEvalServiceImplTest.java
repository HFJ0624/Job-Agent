package com.job.bootstrap.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.job.bootstrap.agent.eval.AgentEvalMatcher;
import com.job.bootstrap.mapper.AgentEvalCaseMapper;
import com.job.bootstrap.mapper.AgentEvalDatasetMapper;
import com.job.bootstrap.mapper.AgentEvalResultMapper;
import com.job.bootstrap.mapper.AgentEvalRunMapper;
import com.job.bootstrap.mapper.AgentTraceLogMapper;
import com.job.bootstrap.service.AgentChatService;
import com.job.bootstrap.service.AiModelGatewayService;
import com.job.bootstrap.service.WorkflowTaskService;
import com.job.common.dto.workflow.WorkflowTaskCreateDTO;
import com.job.common.entity.agent.AgentEvalCase;
import com.job.common.entity.agent.AgentEvalDataset;
import com.job.common.entity.agent.AgentEvalRun;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AgentEvalServiceImpl 异步化测试（任务10）。
 *
 * 覆盖重点:
 * 1. runDataset 只创建 RUNNING 批次并投递 AGENT_EVAL_RUN 任务，不同步执行（agentChatService 不被调用）。
 * 2. executeRun 幂等：批次已结束（SUCCESS/FAILED）则直接返回，不重复执行。
 */
class AgentEvalServiceImplTest {

    private AgentEvalDatasetMapper datasetMapper;
    private AgentEvalCaseMapper caseMapper;
    private AgentEvalRunMapper runMapper;
    private AgentEvalResultMapper resultMapper;
    private AgentTraceLogMapper traceLogMapper;
    private AgentChatService agentChatService;
    private AiModelGatewayService aiModelGatewayService;
    private WorkflowTaskService workflowTaskService;
    private AgentEvalServiceImpl service;

    @BeforeEach
    void setUp() {
        datasetMapper = mock(AgentEvalDatasetMapper.class);
        caseMapper = mock(AgentEvalCaseMapper.class);
        runMapper = mock(AgentEvalRunMapper.class);
        resultMapper = mock(AgentEvalResultMapper.class);
        traceLogMapper = mock(AgentTraceLogMapper.class);
        agentChatService = mock(AgentChatService.class);
        aiModelGatewayService = mock(AiModelGatewayService.class);
        workflowTaskService = mock(WorkflowTaskService.class);

        service = new AgentEvalServiceImpl(
                datasetMapper, caseMapper, runMapper, resultMapper, traceLogMapper,
                agentChatService, aiModelGatewayService, new ObjectMapper(),
                mock(AgentEvalHealthReportBuilder.class), mock(AgentEvalCoreTemplateFactory.class),
                mock(AgentEvalResultDiagnosisBuilder.class), mock(AgentEvalCaseQualityChecker.class),
                new AgentEvalMatcher(), workflowTaskService);
    }

    @Test
    void runDatasetShouldEnqueueNotExecute() {
        AgentEvalDataset dataset = new AgentEvalDataset();
        dataset.setId(1L);
        dataset.setDatasetName("核心回归");
        dataset.setIsDeleted(0);
        when(datasetMapper.selectOne(any())).thenReturn(dataset);
        when(caseMapper.selectList(any())).thenReturn(List.of(new AgentEvalCase()));

        service.runDataset(1L);

        // 投递了 AGENT_EVAL_RUN 工作流任务
        ArgumentCaptor<WorkflowTaskCreateDTO> captor = ArgumentCaptor.forClass(WorkflowTaskCreateDTO.class);
        verify(workflowTaskService).createTask(captor.capture());
        assertThat(captor.getValue().getTaskType()).isEqualTo("AGENT_EVAL_RUN");
        // 关键：同步路径不再直接跑模型链路
        verify(agentChatService, never()).chat(any(), any(), any(), any(), any());
    }

    @Test
    void executeRunShouldSkipWhenAlreadyFinished() {
        AgentEvalRun run = new AgentEvalRun();
        run.setId(9L);
        run.setRunType("ALL");
        run.setStatus("SUCCESS"); // 已结束
        when(runMapper.selectOne(any())).thenReturn(run);

        service.executeRun(9L, null);

        // 幂等：不重复加载用例、不重复执行
        verify(caseMapper, never()).selectList(any());
        verify(agentChatService, never()).chat(any(), any(), any(), any(), any());
    }
}
