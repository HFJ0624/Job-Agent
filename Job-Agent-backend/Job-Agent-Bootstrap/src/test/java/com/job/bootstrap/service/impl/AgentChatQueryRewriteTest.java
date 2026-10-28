package com.job.bootstrap.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.job.bootstrap.agent.context.AgentContextBuilder;
import com.job.bootstrap.agent.executor.AgentPlanExecutionResult;
import com.job.bootstrap.agent.guardrail.AgentGuardrailService;
import com.job.bootstrap.agent.intent.AgentIntentCode;
import com.job.bootstrap.agent.intent.AgentIntentLlmClassifier;
import com.job.bootstrap.agent.intent.AgentIntentRouter;
import com.job.bootstrap.agent.cache.AgentAnswerCacheService;
import com.job.bootstrap.agent.rewrite.AgentQueryRewriter;
import com.job.bootstrap.agent.schema.AgentToolSchemaRegistry;
import com.job.bootstrap.mapper.AiConversationMapper;
import com.job.bootstrap.mapper.AiMessageMapper;
import com.job.bootstrap.rag.service.RagRetrievalService;
import com.job.bootstrap.service.AgentMemoryCaptureService;
import com.job.bootstrap.service.AgentMemoryContextService;
import com.job.bootstrap.service.AgentObservationService;
import com.job.bootstrap.service.AgentPlanExecutorService;
import com.job.bootstrap.service.AgentPlanningService;
import com.job.bootstrap.service.AgentTraceService;
import com.job.bootstrap.service.AiModelGatewayService;
import com.job.common.entity.agent.AiConversation;
import com.job.common.entity.agent.AiMessage;
import com.job.common.vo.agent.AgentChatVO;
import com.job.common.vo.agent.AgentGuardrailResult;
import com.job.common.vo.agent.AgentMemoryContextVO;
import com.job.common.vo.agent.AgentPlanVO;
import com.job.enums.AiModelTier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 主链路查询改写接线测试（任务 G8）。
 *
 * 覆盖重点:
 * 1. 带指代的消息 + 有历史 → 改写出自包含查询，Planner 收到的是改写后的消息；
 * 2. RAG 预检索同样使用改写后的查询；
 * 3. 自包含消息 → 不发起改写，Planner 收到原消息。
 */
class AgentChatQueryRewriteTest {

    private static final Long USER_ID = 100L;
    private static final Long CONVERSATION_ID = 88L;

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
    private final AgentObservationService agentObservationService = mock(AgentObservationService.class);
    private final AgentAnswerCacheService answerCacheService = mock(AgentAnswerCacheService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    // 真改写器（网关与观测用 mock）：验证“主链路真的接了改写”
    private final AgentQueryRewriter queryRewriter =
            new AgentQueryRewriter(aiModelGatewayService, agentObservationService);

    private final AgentChatServiceImpl service = new AgentChatServiceImpl(
            aiConversationMapper, aiMessageMapper, aiModelGatewayService, agentTraceService,
            ragRetrievalService, agentIntentRouter, agentIntentLlmClassifier, agentPlanningService,
            agentPlanExecutorService, agentMemoryCaptureService, agentMemoryContextService,
            agentToolSchemaRegistry, agentGuardrailService, agentContextBuilder, queryRewriter,
            answerCacheService, objectMapper);

    @BeforeEach
    void setUp() {
        // 本用例聚焦改写接线：缓存恒未命中，不干扰断言
        when(answerCacheService.lookup(any(), any())).thenReturn(java.util.Optional.empty());
        when(agentGuardrailService.checkUserInput(anyLong(), anyString())).thenReturn(AgentGuardrailResult.allow());
        when(agentGuardrailService.maskSensitiveData(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(agentGuardrailService.sanitizeFinalAnswer(any(), any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(agentIntentRouter.routeDetailed(anyString()))
                .thenReturn(new AgentIntentRouter.IntentResolution(AgentIntentCode.GENERAL_CHAT, true));
        when(agentMemoryContextService.buildContext(any(), any())).thenReturn(new AgentMemoryContextVO());
        when(agentMemoryCaptureService.captureFromUserMessage(any(), any(), any(), any())).thenReturn(List.of());
        when(agentPlanExecutorService.executePlan(anyLong(), anyLong(), org.mockito.ArgumentMatchers.anyBoolean())).thenReturn(
                AgentPlanExecutionResult.builder()
                        .success(true).status("COMPLETED").message("执行完成").steps(List.of())
                        .build());
        when(agentPlanningService.createPlan(any(), any(), any(), any(), any(), any())).thenReturn(buildPlan());
        when(ragRetrievalService.search(anyLong(), anyString(), any())).thenReturn(List.of());
        when(agentContextBuilder.build(any(), any(), any()))
                .thenReturn(new AgentContextBuilder.BuiltContext(
                        "用户: 帮我推荐几个Java岗位\n助手: 为你推荐了3个，第2个是上海电商公司的Java开发", "", false, 20));
        when(agentContextBuilder.previewExecution(any())).thenReturn("执行结果预览");
        when(aiModelGatewayService.chat(eq("AGENT_SUMMARY"), any(), any(), anyString(), anyLong(), any()))
                .thenReturn("第2个岗位匹配度很高，建议投递");
    }

    private AgentPlanVO buildPlan() {
        AgentPlanVO plan = new AgentPlanVO();
        plan.setId(1L);
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

    private AiConversation existingConversation() {
        AiConversation conversation = new AiConversation();
        conversation.setId(CONVERSATION_ID);
        conversation.setUserId(USER_ID);
        conversation.setConversationType("JOB_AGENT");
        conversation.setIsDeleted(0);
        return conversation;
    }

    @Test
    void referenceMessageShouldBeRewrittenBeforePlanning() {
        when(aiConversationMapper.selectById(CONVERSATION_ID)).thenReturn(existingConversation());
        // 历史消息（倒序：最新在前）；build 的 dropCurrentTurn 会去掉最新一条
        when(aiMessageMapper.selectList(any())).thenReturn(List.of(
                message(CONVERSATION_ID, "ASSISTANT", "为你推荐了3个，第2个是上海电商公司的Java开发"),
                message(CONVERSATION_ID, "USER", "帮我推荐几个Java岗位")
        ));
        // 改写场景返回自包含查询
        when(aiModelGatewayService.chat(
                eq("QUERY_REWRITE"), eq(AiModelTier.LIGHT), any(), anyString(), anyLong(), any()))
                .thenReturn("刚才推荐的第二个Java岗位的匹配度如何");

        AgentChatVO vo = service.chat(USER_ID, CONVERSATION_ID, null, "那第二个呢", null, false);

        assertThat(vo.getAnswer()).isEqualTo("第2个岗位匹配度很高，建议投递");
        // Planner 收到的是改写后的自包含查询（createPlan 第 5 个参数）
        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(agentPlanningService).createPlan(
                eq(USER_ID), eq(CONVERSATION_ID), any(), any(), messageCaptor.capture(), any());
        assertThat(messageCaptor.getValue()).isEqualTo("刚才推荐的第二个Java岗位的匹配度如何");
        // 改写生效记了 REWRITE 观测事件
        verify(agentObservationService, org.mockito.Mockito.atLeastOnce()).recordEvent(
                any());
    }

    @Test
    void selfContainedMessageShouldNotTriggerRewrite() {
        when(aiConversationMapper.selectById(CONVERSATION_ID)).thenReturn(existingConversation());
        when(aiMessageMapper.selectList(any())).thenReturn(List.of(
                message(CONVERSATION_ID, "ASSISTANT", "为你推荐了3个"),
                message(CONVERSATION_ID, "USER", "帮我推荐几个Java岗位")
        ));

        service.chat(USER_ID, CONVERSATION_ID, null, "帮我分析一下简历", null, false);

        // 自包含消息：不改写，Planner 收到原消息
        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(agentPlanningService).createPlan(
                eq(USER_ID), eq(CONVERSATION_ID), any(), any(), messageCaptor.capture(), any());
        assertThat(messageCaptor.getValue()).isEqualTo("帮我分析一下简历");
        // 没有发起 QUERY_REWRITE 模型调用
        verify(aiModelGatewayService, org.mockito.Mockito.never()).chat(
                eq("QUERY_REWRITE"), any(), any(), anyString(), anyLong(), any());
    }

    private AiMessage message(Long conversationId, String role, String content) {
        AiMessage aiMessage = new AiMessage();
        aiMessage.setConversationId(conversationId);
        aiMessage.setRole(role);
        aiMessage.setContent(content);
        aiMessage.setCreateTime(new Date());
        aiMessage.setIsDeleted(0);
        return aiMessage;
    }
}
