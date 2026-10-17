package com.job.bootstrap.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.job.bootstrap.agent.context.AgentContextBuilder;
import com.job.bootstrap.agent.executor.AgentPlanExecutionResult;
import com.job.bootstrap.agent.guardrail.AgentGuardrailService;
import com.job.bootstrap.agent.intent.AgentIntentCode;
import com.job.bootstrap.agent.intent.AgentIntentLlmClassifier;
import com.job.bootstrap.agent.intent.AgentIntentRouter;
import com.job.bootstrap.agent.schema.AgentToolSchemaRegistry;
import com.job.bootstrap.mapper.AiConversationMapper;
import com.job.bootstrap.mapper.AiMessageMapper;
import com.job.bootstrap.rag.service.RagRetrievalService;
import com.job.bootstrap.service.AgentMemoryCaptureService;
import com.job.bootstrap.service.AgentMemoryContextService;
import com.job.bootstrap.service.AgentPlanExecutorService;
import com.job.bootstrap.service.AgentPlanningService;
import com.job.bootstrap.service.AgentTraceService;
import com.job.bootstrap.service.AiModelGatewayService;
import com.job.common.vo.agent.AgentChatVO;
import com.job.common.vo.agent.AgentGuardrailResult;
import com.job.common.vo.agent.AgentMemoryContextVO;
import com.job.common.vo.agent.AgentPlanVO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Agent 主链路 RAG 降级测试（RAG 接入主链路 · 任务2）。
 *
 * 覆盖重点:
 * 1. RAG 检索抛异常时，聊天主流程不中断，仍返回模型总结回答。
 * 2. 降级路径会落一条 ragStatus=FAILED 的 RAG 预检索 Tool Trace，便于后台排查。
 * 3. 总结阶段改用“知识库检索失败”的增强消息，模型只基于通用知识回答（不断言具体文案，只保证不中断）。
 */
class AgentChatRagFallbackTest {

    private static final Long USER_ID = 100L;
    private static final Long PLAN_ID = 1L;

    private final AiConversationMapper aiConversationMapper = mock(AiConversationMapper.class);
    private final AiMessageMapper aiMessageMapper = mock(AiMessageMapper.class);
    private final AiModelGatewayService aiModelGatewayService = mock(AiModelGatewayService.class);
    private final AgentTraceService agentTraceService = mock(AgentTraceService.class);
    private final RagRetrievalService ragRetrievalService = mock(RagRetrievalService.class);
    private final AgentIntentRouter agentIntentRouter = mock(AgentIntentRouter.class);
    private final AgentIntentLlmClassifier agentIntentLlmClassifier = mock(AgentIntentLlmClassifier.class);
    private final AgentPlanningService agentPlanningService = mock(AgentPlanningService.class);
    private final AgentPlanExecutorService agentPlanExecutorService = mock(AgentPlanExecutorService.class);
    private final AgentMemoryCaptureService agentMemoryCaptureService = mock(AgentMemoryCaptureService.class);
    private final AgentMemoryContextService agentMemoryContextService = mock(AgentMemoryContextService.class);
    private final AgentToolSchemaRegistry agentToolSchemaRegistry = mock(AgentToolSchemaRegistry.class);
    private final AgentGuardrailService agentGuardrailService = mock(AgentGuardrailService.class);
    private final AgentContextBuilder agentContextBuilder = mock(AgentContextBuilder.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final AgentChatServiceImpl service = new AgentChatServiceImpl(
            aiConversationMapper, aiMessageMapper, aiModelGatewayService, agentTraceService,
            ragRetrievalService, agentIntentRouter, agentIntentLlmClassifier, agentPlanningService, agentPlanExecutorService,
            agentMemoryCaptureService, agentMemoryContextService, agentToolSchemaRegistry,
            agentGuardrailService, agentContextBuilder, objectMapper);

    @Test
    void ragFailureShouldNotBreakChatAndTraceFailed() {
        when(agentGuardrailService.checkUserInput(anyLong(), anyString())).thenReturn(AgentGuardrailResult.allow());
        when(agentGuardrailService.maskSensitiveData(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(agentGuardrailService.sanitizeFinalAnswer(any(), any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(agentIntentRouter.routeDetailed(anyString()))
                .thenReturn(new AgentIntentRouter.IntentResolution(AgentIntentCode.GENERAL_CHAT, true));
        when(agentPlanningService.createPlan(any(), any(), any(), any(), any(), any())).thenReturn(buildPlan());
        when(agentMemoryContextService.buildContext(any(), any())).thenReturn(new AgentMemoryContextVO());
        when(agentMemoryCaptureService.captureFromUserMessage(any(), any(), any(), any())).thenReturn(List.of());
        when(agentPlanExecutorService.executePlan(anyLong(), anyLong(), anyBoolean())).thenReturn(
                AgentPlanExecutionResult.builder()
                        .success(true).status("COMPLETED").message("执行完成").steps(List.of())
                        .build());
        when(aiModelGatewayService.chat(any(), any(), any(), any(), any(), any())).thenReturn("已基于通用求职知识回答");
        // 关键：RAG 检索抛异常，触发降级
        when(ragRetrievalService.search(anyLong(), anyString(), any())).thenThrow(new RuntimeException("pgvector 连接失败"));
        when(agentContextBuilder.build(any(), any(), any()))
                .thenReturn(new AgentContextBuilder.BuiltContext("1. 用户: 之前的问题", "", false, 5));
        when(agentContextBuilder.previewExecution(any())).thenReturn("执行结果预览");

        AgentChatVO vo = service.chat(USER_ID, null, null, "帮我分析简历", null, false);

        // 聊天不中断，正常返回回答
        assertThat(vo).isNotNull();
        assertThat(vo.getAnswer()).isEqualTo("已基于通用求职知识回答");
        // 落了一条 ragStatus=FAILED 的 RAG 预检索 Tool Trace
        verify(agentTraceService).saveToolTrace(
                eq(USER_ID), any(), any(), eq("RagPreRetrieval.searchKnowledge"),
                any(), any(), eq("FAILED"), any(), any());
    }

    private AgentPlanVO buildPlan() {
        AgentPlanVO plan = new AgentPlanVO();
        plan.setId(PLAN_ID);
        plan.setIntentCode("GENERAL_CHAT");
        plan.setUserGoal("分析简历");
        plan.setPlanTitle("通用求职问答计划");
        plan.setPlanSummary("基于知识库回答");
        plan.setStatus("PLANNED");
        plan.setExtractedParamsJson("{}");
        plan.setMissingParamsJson("[]");
        plan.setSteps(List.of());
        return plan;
    }
}
