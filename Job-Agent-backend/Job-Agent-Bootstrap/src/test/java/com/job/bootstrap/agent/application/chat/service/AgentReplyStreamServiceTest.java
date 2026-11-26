package com.job.bootstrap.agent.application.chat.service;

import com.job.bootstrap.ai.TokenConsumer;
import com.job.bootstrap.service.AiModelGatewayService;
import com.job.enums.AiModelTier;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 流式答复服务：只验证"复用 AGENT_SUMMARY 场景 + 透传 token 消费者"，
 * 具体网关行为由网关自身用例覆盖。
 */
class AgentReplyStreamServiceTest {

    private final AiModelGatewayService gateway = mock(AiModelGatewayService.class);
    private final AgentReplyStreamService service = new AgentReplyStreamService(gateway);

    @Test
    void shouldDelegateToGatewaySummarySceneAndReturnFullText() {
        TokenConsumer consumer = token -> { };
        when(gateway.chatStream(eq("AGENT_SUMMARY"), eq(AiModelTier.PRO), anyMap(),
                anyString(), eq(1L), anyString(), any(TokenConsumer.class)))
                .thenReturn("改写后的答复");

        String result = service.stream("已校验答复原文", 1L, "trace-1", consumer);

        assertThat(result).isEqualTo("改写后的答复");
        verify(gateway).chatStream(eq("AGENT_SUMMARY"), eq(AiModelTier.PRO),
                any(Map.class), anyString(), eq(1L), eq("trace-1"), eq(consumer));
    }
}
