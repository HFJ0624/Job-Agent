package com.job.bootstrap.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.job.bootstrap.agent.cache.AgentAnswerCacheService;
import com.job.bootstrap.agent.cache.AgentCacheProperties;
import com.job.bootstrap.agent.cache.InMemorySemanticCacheStore;
import com.job.bootstrap.agent.context.AgentContextBuilder;
import com.job.bootstrap.agent.executor.AgentPlanExecutionResult;
import com.job.bootstrap.agent.guardrail.AgentGuardrailService;
import com.job.bootstrap.agent.intent.AgentIntentCode;
import com.job.bootstrap.agent.intent.AgentIntentLlmClassifier;
import com.job.bootstrap.agent.intent.AgentIntentRouter;
import com.job.bootstrap.agent.rewrite.AgentQueryRewriter;
import com.job.bootstrap.agent.schema.AgentToolSchemaRegistry;
import com.job.bootstrap.mapper.AiConversationMapper;
import com.job.bootstrap.mapper.AiMessageMapper;
import com.job.bootstrap.rag.service.RagEmbeddingService;
import com.job.bootstrap.rag.service.RagRetrievalService;
import com.job.bootstrap.service.AgentMemoryCaptureService;
import com.job.bootstrap.service.AgentMemoryContextService;
import com.job.bootstrap.service.AgentObservationService;
import com.job.bootstrap.service.AgentPlanExecutorService;
import com.job.bootstrap.service.AgentPlanningService;
import com.job.bootstrap.service.AgentTraceService;
import com.job.bootstrap.service.AiModelGatewayService;
import com.job.common.agent.tool.AgentToolSchema;
import com.job.common.entity.agent.AiConversation;
import com.job.common.entity.agent.AiMessage;
import com.job.common.vo.agent.AgentChatVO;
import com.job.common.vo.agent.AgentGuardrailResult;
import com.job.common.vo.agent.AgentMemoryContextVO;
import com.job.common.vo.agent.AgentPlanStepVO;
import com.job.common.vo.agent.AgentPlanVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 主链路语义缓存接线测试（任务 G10）。
 *
 * 覆盖重点（真实缓存服务 + 确定性 embedding，非 mock）:
 * 1. 同一问题问两遍：第一遍走完整链路并写缓存，第二遍命中短路（不再规划/执行），
 *    答案与第一遍一致，planId 为 null；
 * 2. 需要用户确认的答案不写缓存（确认流程结束后缓存仍为空）。
 */
class AgentChatCacheTest {

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
    private final RagEmbeddingService ragEmbeddingService = mock(RagEmbeddingService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    // 真实缓存链路（内存存储 + 确定性 embedding），验证端到端行为
    private final AgentCacheProperties cacheProperties = new AgentCacheProperties();
    private final AgentAnswerCacheService answerCacheService;

    private final AgentQueryRewriter queryRewriter;
    private final AgentChatServiceImpl service;

    {
        cacheProperties.setEnabled(true);
        cacheProperties.setSimilarityThreshold(0.9);
        cacheProperties.setTtlMinutes(30);
        answerCacheService = new AgentAnswerCacheService(
                cacheProperties, new InMemorySemanticCacheStore(cacheProperties),
                ragEmbeddingService, agentObservationService);
        queryRewriter = new AgentQueryRewriter(aiModelGatewayService, agentObservationService);
        service = new AgentChatServiceImpl(
                aiConversationMapper, aiMessageMapper, aiModelGatewayService, agentTraceService,
                ragRetrievalService, agentIntentRouter, agentIntentLlmClassifier, agentPlanningService,
                agentPlanExecutorService, agentMemoryCaptureService, agentMemoryContextService,
                agentToolSchemaRegistry, agentGuardrailService, agentContextBuilder, queryRewriter,
                answerCacheService, objectMapper);
    }

    @BeforeEach
    void setUp() {
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
        when(agentPlanningService.createPlan(any(), any(), any(), any(), any(), any()))
                .thenReturn(buildPlan());
        when(ragRetrievalService.search(anyLong(), anyString(), any())).thenReturn(List.of());
        when(agentContextBuilder.build(any(), any(), any()))
                .thenReturn(new AgentContextBuilder.BuiltContext("用户: 帮我分析简历\n助手: 得分86", "", false, 20));
        when(agentContextBuilder.previewExecution(any())).thenReturn("执行结果预览");
        when(aiModelGatewayService.chat(eq("AGENT_SUMMARY"), any(), any(), anyString(), anyLong(), any()))
                .thenReturn("你的简历得分86，建议补充项目量化数据");
        when(aiConversationMapper.selectById(CONVERSATION_ID)).thenReturn(existingConversation());
        when(aiMessageMapper.selectList(any())).thenReturn(List.of(
                message("ASSISTANT", "得分86"), message("USER", "帮我分析简历")));
        // 确定性 embedding：同一文本 → 同一向量（相似度 1.0），不同文本 → 不同向量
        when(ragEmbeddingService.embed(anyString())).thenAnswer(invocation -> {
            String text = invocation.getArgument(0);
            java.util.Random random = new java.util.Random(text.hashCode());
            float[] vector = new float[8];
            for (int i = 0; i < vector.length; i++) {
                vector[i] = random.nextFloat();
            }
            return vector;
        });
    }

    private AiConversation existingConversation() {
        AiConversation conversation = new AiConversation();
        conversation.setId(CONVERSATION_ID);
        conversation.setUserId(USER_ID);
        conversation.setConversationType("JOB_AGENT");
        conversation.setIsDeleted(0);
        return conversation;
    }

    private AiMessage message(String role, String content) {
        AiMessage aiMessage = new AiMessage();
        aiMessage.setConversationId(CONVERSATION_ID);
        aiMessage.setRole(role);
        aiMessage.setContent(content);
        aiMessage.setCreateTime(new Date());
        aiMessage.setIsDeleted(0);
        return aiMessage;
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

    @Test
    void repeatedQuestionShouldBeServedFromCache() {
        // 第一遍：完整链路，答案写回缓存
        AgentChatVO first = service.chat(USER_ID, CONVERSATION_ID, null, "帮我分析简历", null, false);
        assertThat(first.getAnswer()).isEqualTo("你的简历得分86，建议补充项目量化数据");
        assertThat(first.getPlanId()).isEqualTo(1L);
        verify(agentPlanningService, times(1)).createPlan(any(), any(), any(), any(), any(), any());

        // 第二遍：同一问题 → 缓存命中，短路
        AgentChatVO second = service.chat(USER_ID, CONVERSATION_ID, null, "帮我分析简历", null, false);

        assertThat(second.getAnswer()).isEqualTo("你的简历得分86，建议补充项目量化数据");
        assertThat(second.getPlanId()).isNull();
        // 没有第二次规划/执行/总结
        verify(agentPlanningService, times(1)).createPlan(any(), any(), any(), any(), any(), any());
        verify(agentPlanExecutorService, times(1)).executePlan(anyLong(), anyLong(), org.mockito.ArgumentMatchers.anyBoolean());
        verify(aiModelGatewayService, times(1)).chat(
                eq("AGENT_SUMMARY"), any(), any(), anyString(), anyLong(), any());
    }

    @Test
    void confirmationRequiredAnswerShouldNotBeCached() {
        AgentPlanVO confirmationPlan = buildPlan();
        AgentPlanStepVO step = new AgentPlanStepVO();
        step.setStepNo(1);
        step.setToolName("EmailConnectorTool.sendEmail");
        confirmationPlan.setSteps(List.of(step));
        when(agentPlanningService.createPlan(any(), any(), any(), any(), any(), any()))
                .thenReturn(confirmationPlan);
        when(agentToolSchemaRegistry.findByToolExpression("EmailConnectorTool.sendEmail"))
                .thenReturn(List.of(AgentToolSchema.builder()
                        .toolName("EmailConnectorTool.sendEmail")
                        .requiresUserConfirmation(true)
                        .build()));

        AgentChatVO vo = service.chat(USER_ID, CONVERSATION_ID, null, "帮我发封邮件", null, false);

        assertThat(vo.getRequiresUserConfirmation()).isTrue();
        // 确认中的答案不进缓存：相同问题再问依然查不到
        Optional<String> cached = answerCacheService.lookup(USER_ID, "帮我发封邮件");
        assertThat(cached).isEmpty();
    }
}
