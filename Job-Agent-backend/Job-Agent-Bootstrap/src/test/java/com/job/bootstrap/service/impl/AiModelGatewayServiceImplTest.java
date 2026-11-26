package com.job.bootstrap.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.job.bootstrap.ai.AiGatewayProperties;
import com.job.bootstrap.ai.AiModelCallResponse;
import com.job.bootstrap.ai.AiModelHttpClient;
import com.job.bootstrap.ai.AiModelRateLimiter;
import com.job.bootstrap.ai.AiRenderedPrompt;
import com.job.bootstrap.ai.AiRetryPolicy;
import com.job.bootstrap.ai.TokenConsumer;
import com.job.bootstrap.mapper.AiModelCallLogMapper;
import com.job.bootstrap.mapper.AiModelCircuitStateMapper;
import com.job.bootstrap.mapper.AiModelConfigMapper;
import com.job.bootstrap.mapper.AiModelRouteMapper;
import com.job.bootstrap.service.AgentObservationService;
import com.job.bootstrap.service.AiPromptRuntimeService;
import com.job.common.entity.ai.AiModelCallLog;
import com.job.common.entity.ai.AiModelCircuitState;
import com.job.common.entity.ai.AiModelConfig;
import com.job.common.entity.ai.AiModelRoute;
import com.job.common.entity.ai.AiPromptVersion;
import com.job.enums.AiCircuitStatus;
import com.job.enums.AiModelTier;
import com.job.exception.BizException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AiModelGatewayServiceImpl 稳定性集成测试（任务8）。
 *
 * 覆盖重点（mock 掉 AiModelHttpClient，不调用真实供应商）:
 * 1. 可重试错误（5xx）→ 重试到 maxAttempts 次。
 * 2. 4xx 业务错误 → 不重试，直接结束。
 * 3. 限流 → 第 2 次快速失败，且不再调 HTTP。
 * 4. 熔断 OPEN（冷却未过）→ 跳过模型，不调 HTTP。
 * 5. HALF_OPEN（冷却已过）→ 放行 1 个探测请求，成功返回。
 */
class AiModelGatewayServiceImplTest {

    private AiModelRouteMapper aiModelRouteMapper;
    private AiModelConfigMapper aiModelConfigMapper;
    private AiModelCallLogMapper aiModelCallLogMapper;
    private AiModelCircuitStateMapper aiModelCircuitStateMapper;
    private AiPromptRuntimeService aiPromptRuntimeService;
    private AgentObservationService agentObservationService;
    private AiModelHttpClient aiModelHttpClient;
    private AiModelRateLimiter rateLimiter;
    private AiRetryPolicy retryPolicy;
    private AiGatewayProperties properties;
    private AiModelGatewayServiceImpl gateway;

    private AiModelConfig model;

    @BeforeEach
    void setUp() {
        aiModelRouteMapper = mock(AiModelRouteMapper.class);
        aiModelConfigMapper = mock(AiModelConfigMapper.class);
        aiModelCallLogMapper = mock(AiModelCallLogMapper.class);
        aiModelCircuitStateMapper = mock(AiModelCircuitStateMapper.class);
        aiPromptRuntimeService = mock(AiPromptRuntimeService.class);
        agentObservationService = mock(AgentObservationService.class);
        aiModelHttpClient = mock(AiModelHttpClient.class);

        properties = new AiGatewayProperties();
        properties.setRetryBaseDelayMs(1); // 测试用极小退避，避免拖慢
        properties.setRetryMaxDelayMs(2);
        properties.setRetryJitterMs(0);
        rateLimiter = new AiModelRateLimiter(properties);
        retryPolicy = new AiRetryPolicy(properties);

        AiModelRoute route = new AiModelRoute();
        route.setSceneCode("AGENT_SUMMARY");
        route.setPrimaryModelCode("m1");
        route.setPromptCode("p");
        route.setGrayPercent(100);
        route.setStatus("ACTIVE");
        when(aiModelRouteMapper.selectList(any())).thenReturn(List.of(route));

        model = new AiModelConfig();
        model.setModelCode("m1");
        model.setApiKey("k");
        model.setMaxRetries(2);
        model.setCircuitEnabled(0); // 默认关熔断，重试/限流用例不受影响
        model.setTimeoutSeconds(5);
        model.setInputPricePer1k(BigDecimal.ZERO);
        model.setOutputPricePer1k(BigDecimal.ZERO);
        model.setStatus("ACTIVE");
        when(aiModelConfigMapper.selectOne(any())).thenReturn(model);

        AiPromptVersion promptVersion = new AiPromptVersion();
        promptVersion.setId(1L);
        when(aiPromptRuntimeService.renderPrompt(any(), any()))
                .thenReturn(new AiRenderedPrompt(route, promptVersion, "system"));

        when(aiModelCallLogMapper.insert(any(AiModelCallLog.class))).thenReturn(1);
        when(aiModelCircuitStateMapper.update(any(), any())).thenReturn(1);
        when(aiModelCircuitStateMapper.updateById(any(AiModelCircuitState.class))).thenReturn(1);

        gateway = new AiModelGatewayServiceImpl(
                aiModelRouteMapper, aiModelConfigMapper, aiModelCallLogMapper, aiModelCircuitStateMapper,
                aiPromptRuntimeService, agentObservationService, new ObjectMapper(),
                aiModelHttpClient, rateLimiter, retryPolicy, properties);
    }

    @Test
    void retryableErrorShouldRetryUpToMaxAttempts() {
        when(aiModelHttpClient.chat(any(), any(), any()))
                .thenThrow(new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> gateway.chat("AGENT_SUMMARY", java.util.Map.of(), "hi", 100L, "trace"))
                .isInstanceOf(BizException.class);

        verify(aiModelHttpClient, times(3)).chat(any(), any(), any()); // maxRetries=2 → 3 次
    }

    @Test
    void clientErrorShouldNotRetry() {
        when(aiModelHttpClient.chat(any(), any(), any()))
                .thenThrow(new HttpClientErrorException(HttpStatus.BAD_REQUEST));

        assertThatThrownBy(() -> gateway.chat("AGENT_SUMMARY", java.util.Map.of(), "hi", 100L, "trace"))
                .isInstanceOf(BizException.class);

        verify(aiModelHttpClient, times(1)).chat(any(), any(), any()); // 4xx 不重试
    }

    @Test
    void rateLimitedShouldFailFast() {
        properties.setSceneQps(1.0);
        properties.setUserQps(1.0);
        when(aiModelHttpClient.chat(any(), any(), any())).thenReturn(new AiModelCallResponse("答案", 1, 1));

        // 第 1 次放行并成功
        assertThat(gateway.chat("AGENT_SUMMARY", java.util.Map.of(), "hi", 100L, "trace")).isEqualTo("答案");
        // 第 2 次触发限流，快速失败
        assertThatThrownBy(() -> gateway.chat("AGENT_SUMMARY", java.util.Map.of(), "hi", 100L, "trace"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("限流");

        verify(aiModelHttpClient, times(1)).chat(any(), any(), any()); // 限流那次没打到 HTTP
    }

    @Test
    void circuitOpenShouldSkipModel() {
        model.setCircuitEnabled(1);
        AiModelCircuitState state = new AiModelCircuitState();
        state.setId(1L);
        state.setModelCode("m1");
        state.setCircuitStatus(AiCircuitStatus.OPEN.name());
        state.setOpenedUntil(new Date(System.currentTimeMillis() + 60_000L)); // 冷却未过
        when(aiModelCircuitStateMapper.selectOne(any())).thenReturn(state);

        assertThatThrownBy(() -> gateway.chat("AGENT_SUMMARY", java.util.Map.of(), "hi", 100L, "trace"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("熔断");

        verify(aiModelHttpClient, never()).chat(any(), any(), any());
    }

    @Test
    void halfOpenShouldAllowSingleProbe() {
        model.setCircuitEnabled(1);
        AiModelCircuitState state = new AiModelCircuitState();
        state.setId(1L);
        state.setModelCode("m1");
        state.setCircuitStatus(AiCircuitStatus.OPEN.name());
        state.setOpenedUntil(new Date(System.currentTimeMillis() - 60_000L)); // 冷却已过
        when(aiModelCircuitStateMapper.selectOne(any())).thenReturn(state);
        when(aiModelCircuitStateMapper.update(any(), any())).thenReturn(1); // 抢到探测资格
        when(aiModelHttpClient.chat(any(), any(), any())).thenReturn(new AiModelCallResponse("恢复成功", 1, 1));

        String answer = gateway.chat("AGENT_SUMMARY", java.util.Map.of(), "hi", 100L, "trace");

        assertThat(answer).isEqualTo("恢复成功");
        verify(aiModelHttpClient, times(1)).chat(any(), any(), any());
    }

    // ===== 任务 G1：模型分级路由 =====

    @Test
    void shouldPreferTierRouteWhenConfigured() {
        when(aiModelRouteMapper.selectList(any())).thenReturn(List.of(route("p-std", null), route("p-light", "LIGHT")));
        when(aiModelHttpClient.chat(any(), any(), any())).thenReturn(new AiModelCallResponse("ok", 1, 1));

        gateway.chat("AGENT_SUMMARY", AiModelTier.LIGHT, java.util.Map.of(), "hi", 100L, "trace");

        // 命中 LIGHT 档路由（用调用日志的 promptCode 验证选中的路由）
        ArgumentCaptor<AiModelCallLog> logCaptor = ArgumentCaptor.forClass(AiModelCallLog.class);
        verify(aiModelCallLogMapper, atLeastOnce()).insert(logCaptor.capture());
        assertThat(logCaptor.getValue().getPromptCode()).isEqualTo("p-light");
    }

    @Test
    void shouldDegradeToSceneDefaultWhenTierNotConfigured() {
        when(aiModelRouteMapper.selectList(any())).thenReturn(List.of(route("p-std", null))); // 场景内只有 STANDARD
        when(aiModelHttpClient.chat(any(), any(), any())).thenReturn(new AiModelCallResponse("ok", 1, 1));

        gateway.chat("AGENT_SUMMARY", AiModelTier.LIGHT, java.util.Map.of(), "hi", 100L, "trace");

        ArgumentCaptor<AiModelCallLog> logCaptor = ArgumentCaptor.forClass(AiModelCallLog.class);
        verify(aiModelCallLogMapper, atLeastOnce()).insert(logCaptor.capture());
        assertThat(logCaptor.getValue().getPromptCode()).isEqualTo("p-std"); // 降级用场景默认路由
    }

    @Test
    void legacyChatWithoutTierShouldUseStandard() {
        when(aiModelRouteMapper.selectList(any())).thenReturn(List.of(route("p-std", null), route("p-light", "LIGHT")));
        when(aiModelHttpClient.chat(any(), any(), any())).thenReturn(new AiModelCallResponse("ok", 1, 1));

        gateway.chat("AGENT_SUMMARY", java.util.Map.of(), "hi", 100L, "trace"); // 旧 5 参方法

        ArgumentCaptor<AiModelCallLog> logCaptor = ArgumentCaptor.forClass(AiModelCallLog.class);
        verify(aiModelCallLogMapper, atLeastOnce()).insert(logCaptor.capture());
        assertThat(logCaptor.getValue().getPromptCode()).isEqualTo("p-std"); // 等价 STANDARD
    }

    // ===== 任务 G5：流式调用 =====

    @Test
    void streamShouldForwardTokensAndRecordLog() {
        doAnswer(invocation -> {
            TokenConsumer consumer = invocation.getArgument(3);
            consumer.onToken("你");
            consumer.onToken("好");
            return null;
        }).when(aiModelHttpClient).chatStream(any(), any(), any(), any());

        List<String> tokens = new ArrayList<>();
        String answer = gateway.chatStream(
                "AGENT_SUMMARY", AiModelTier.STANDARD, java.util.Map.of(), "hi", 100L, "trace", tokens::add);

        assertThat(answer).isEqualTo("你好");
        assertThat(tokens).containsExactly("你", "好");
        verify(aiModelCallLogMapper, atLeastOnce()).insert(any(AiModelCallLog.class));
    }

    @Test
    void streamFailureShouldFallbackToBlocking() {
        doThrow(new RuntimeException("provider does not support stream"))
                .when(aiModelHttpClient).chatStream(any(), any(), any(), any());
        when(aiModelHttpClient.chat(any(), any(), any())).thenReturn(new AiModelCallResponse("完整答案", 3, 3));

        List<String> tokens = new ArrayList<>();
        String answer = gateway.chatStream(
                "AGENT_SUMMARY", AiModelTier.STANDARD, java.util.Map.of(), "hi", 100L, "trace", tokens::add);

        // 流式失败降级阻塞：整段作为一个 token，调用方无感知
        assertThat(answer).isEqualTo("完整答案");
        assertThat(tokens).containsExactly("完整答案");
        verify(aiModelHttpClient).chat(any(), any(), any());
    }

    // 构造测试路由：同场景、灰度 100、ACTIVE，tier 可空（空按 STANDARD）
    private AiModelRoute route(String promptCode, String tier) {
        AiModelRoute testRoute = new AiModelRoute();
        testRoute.setSceneCode("AGENT_SUMMARY");
        testRoute.setPrimaryModelCode("m1");
        testRoute.setPromptCode(promptCode);
        testRoute.setGrayPercent(100);
        testRoute.setStatus("ACTIVE");
        testRoute.setTier(tier);
        return testRoute;
    }
}
