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
import com.job.common.entity.agent.AgentEvalResult;
import com.job.common.entity.agent.AgentEvalRun;
import com.job.common.vo.agent.AgentChatVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
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
 * 3. LLM-as-Judge（任务 G3）：judge 分数/结论落库；低于阈值判失败；网关不可用时降级不阻断回归。
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

    // ===== 任务 G3：LLM-as-Judge =====

    @Test
    void executeRunShouldRecordJudgeResult() {
        stubCaseRun();
        when(agentChatService.chat(any(), any(), any(), any(), any()))
                .thenReturn(chatVO("推荐Java后端岗位，Spring框架经验加分"));
        when(traceLogMapper.selectList(any())).thenReturn(List.of());
        when(aiModelGatewayService.chat(any(), any(), any(), any(), any())).thenReturn(
                "{\"score\":85,\"pass\":true,\"reason\":\"准确且完整\",\"hallucinationRisk\":\"LOW\",\"suggestions\":[\"可补充城市\"]}");
        when(resultMapper.insert(any(AgentEvalResult.class))).thenReturn(1);

        service.executeRun(9L, 5L);

        ArgumentCaptor<AgentEvalResult> captor = ArgumentCaptor.forClass(AgentEvalResult.class);
        verify(resultMapper).insert(captor.capture());
        AgentEvalResult saved = captor.getValue();
        assertThat(saved.getJudgeScore()).isEqualByComparingTo(BigDecimal.valueOf(85));
        assertThat(saved.getJudgePass()).isEqualTo(1);
        assertThat(saved.getJudgeDetailJson()).contains("hallucinationRisk");
        assertThat(saved.getPassStatus()).isEqualTo(1);
    }

    @Test
    void executeRunShouldMarkJudgeFailedBelowThreshold() {
        stubCaseRun();
        when(agentChatService.chat(any(), any(), any(), any(), any()))
                .thenReturn(chatVO("推荐Java后端岗位"));
        when(traceLogMapper.selectList(any())).thenReturn(List.of());
        when(aiModelGatewayService.chat(any(), any(), any(), any(), any())).thenReturn(
                "{\"score\":50,\"pass\":false,\"reason\":\"覆盖不足\",\"hallucinationRisk\":\"MEDIUM\"}");
        when(resultMapper.insert(any(AgentEvalResult.class))).thenReturn(1);

        service.executeRun(9L, 5L);

        ArgumentCaptor<AgentEvalResult> captor = ArgumentCaptor.forClass(AgentEvalResult.class);
        verify(resultMapper).insert(captor.capture());
        AgentEvalResult saved = captor.getValue();
        assertThat(saved.getJudgeScore()).isEqualByComparingTo(BigDecimal.valueOf(50));
        assertThat(saved.getJudgePass()).isEqualTo(0);
        assertThat(saved.getPassStatus()).isEqualTo(0);
        assertThat(saved.getFailReason()).contains("Judge");
    }

    @Test
    void executeRunShouldDegradeWhenJudgeUnavailable() {
        stubCaseRun();
        when(agentChatService.chat(any(), any(), any(), any(), any()))
                .thenReturn(chatVO("推荐Java后端岗位，Spring框架经验加分"));
        when(traceLogMapper.selectList(any())).thenReturn(List.of());
        // EVAL_JUDGE 场景未配置/模型不可用：Judge 降级，不影响评测主流程
        when(aiModelGatewayService.chat(any(), any(), any(), any(), any()))
                .thenThrow(new RuntimeException("场景未配置"));
        when(resultMapper.insert(any(AgentEvalResult.class))).thenReturn(1);

        service.executeRun(9L, 5L);

        ArgumentCaptor<AgentEvalResult> captor = ArgumentCaptor.forClass(AgentEvalResult.class);
        verify(resultMapper).insert(captor.capture());
        AgentEvalResult saved = captor.getValue();
        assertThat(saved.getJudgePass()).isNull();
        assertThat(saved.getJudgeReason()).contains("Judge 调用失败");
        // 规则分通过：judge 降级不阻断回归
        assertThat(saved.getPassStatus()).isEqualTo(1);
    }

    // 构造一条 CASE 批次 + 带关键词/阈值 configured 的用例（不配置工具/RAG 断言，聚焦 judge）
    private void stubCaseRun() {
        AgentEvalRun run = new AgentEvalRun();
        run.setId(9L);
        run.setRunType("CASE");
        run.setStatus("RUNNING");
        when(runMapper.selectOne(any())).thenReturn(run);

        AgentEvalCase evalCase = new AgentEvalCase();
        evalCase.setId(5L);
        evalCase.setDatasetId(1L);
        evalCase.setUserId(1L);
        evalCase.setCaseName("Java岗位推荐");
        evalCase.setInputMessage("推荐几个Java岗位");
        evalCase.setExpectedAnswerKeywords("Java,Spring");
        evalCase.setMinAnswerScore(BigDecimal.valueOf(60));
        when(caseMapper.selectOne(any())).thenReturn(evalCase);
    }

    private AgentChatVO chatVO(String answer) {
        AgentChatVO vo = new AgentChatVO();
        vo.setConversationId(99L);
        vo.setAnswer(answer);
        return vo;
    }
}
