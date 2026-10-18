package com.job.bootstrap.agent.rewrite;

import com.job.bootstrap.observability.AgentObservationRecord;
import com.job.bootstrap.service.AgentObservationService;
import com.job.bootstrap.service.AiModelGatewayService;
import com.job.enums.AgentObservationEventType;
import com.job.enums.AiModelTier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AgentQueryRewriter 单元测试（任务 G8）。
 *
 * 覆盖重点:
 * 1. 无历史/首轮 → 原样返回，不调模型；
 * 2. 自包含消息（无指代信号）→ 原样返回，不调模型（成本门控）；
 * 3. 含指代信号 + 有历史 → 走 QUERY_REWRITE 场景（LIGHT 档），返回清洗后的改写；
 * 4. 模型输出带引号/前缀噪音 → 清洗；
 * 5. 模型输出为空/超长 → 回退原消息；
 * 6. 网关异常 → 回退原消息（降级不挡主流程）；
 * 7. 改写生效 → 记 REWRITE 观测事件；未改写 → 不记。
 */
class AgentQueryRewriterTest {

    private final AiModelGatewayService gateway = mock(AiModelGatewayService.class);
    private final AgentObservationService observationService = mock(AgentObservationService.class);

    private final AgentQueryRewriter rewriter = new AgentQueryRewriter(gateway, observationService);

    private static final String HISTORY = "用户: 帮我推荐几个Java岗位\n助手: 为你推荐了3个Java后端岗位，第2个是上海某电商公司的Java开发";

    @BeforeEach
    void setUp() {
        when(gateway.chat(eq("QUERY_REWRITE"), eq(AiModelTier.LIGHT), any(), anyString(), anyLong(), any()))
                .thenReturn("刚才推荐的第二个Java岗位的匹配度如何");
    }

    @Test
    void blankHistoryShouldReturnOriginal() {
        String result = rewriter.rewrite(100L, "trace-1", "那第二个呢", "");

        assertThat(result).isEqualTo("那第二个呢");
        verify(gateway, never()).chat(any(), any(), any(), anyString(), anyLong(), any());
    }

    @Test
    void selfContainedMessageShouldSkipModelCall() {
        String result = rewriter.rewrite(100L, "trace-2", "帮我分析一下简历", HISTORY);

        assertThat(result).isEqualTo("帮我分析一下简历");
        verify(gateway, never()).chat(any(), any(), any(), anyString(), anyLong(), any());
    }

    @Test
    void referenceMessageShouldBeRewritten() {
        String result = rewriter.rewrite(100L, "trace-3", "那第二个呢", HISTORY);

        assertThat(result).isEqualTo("刚才推荐的第二个Java岗位的匹配度如何");
        verify(gateway, times(1)).chat(
                eq("QUERY_REWRITE"), eq(AiModelTier.LIGHT), any(), anyString(), eq(100L), any());
    }

    @Test
    void noisyOutputShouldBeSanitized() {
        when(gateway.chat(eq("QUERY_REWRITE"), eq(AiModelTier.LIGHT), any(), anyString(), anyLong(), any()))
                .thenReturn("改写后的查询:“刚才推荐的第二个Java岗位的匹配度如何”");

        String result = rewriter.rewrite(100L, "trace-4", "那第二个呢", HISTORY);

        assertThat(result).isEqualTo("刚才推荐的第二个Java岗位的匹配度如何");
    }

    @Test
    void emptyOrTooLongOutputShouldFallbackToOriginal() {
        when(gateway.chat(eq("QUERY_REWRITE"), eq(AiModelTier.LIGHT), any(), anyString(), anyLong(), any()))
                .thenReturn("   ");
        assertThat(rewriter.rewrite(100L, "trace-5", "那第二个呢", HISTORY)).isEqualTo("那第二个呢");

        when(gateway.chat(eq("QUERY_REWRITE"), eq(AiModelTier.LIGHT), any(), anyString(), anyLong(), any()))
                .thenReturn("x".repeat(600));
        assertThat(rewriter.rewrite(100L, "trace-6", "那第二个呢", HISTORY)).isEqualTo("那第二个呢");
    }

    @Test
    void gatewayFailureShouldDegradeToOriginal() {
        when(gateway.chat(eq("QUERY_REWRITE"), eq(AiModelTier.LIGHT), any(), anyString(), anyLong(), any()))
                .thenThrow(new RuntimeException("场景未配置"));

        String result = rewriter.rewrite(100L, "trace-7", "那第二个呢", HISTORY);

        assertThat(result).isEqualTo("那第二个呢");
    }

    @Test
    void effectiveRewriteShouldRecordObservationEvent() {
        rewriter.rewrite(100L, "trace-8", "那第二个呢", HISTORY);

        org.mockito.ArgumentCaptor<AgentObservationRecord> captor =
                org.mockito.ArgumentCaptor.forClass(AgentObservationRecord.class);
        verify(observationService, times(1)).recordEvent(captor.capture());
        assertThat(captor.getValue().getEventType()).isEqualTo(AgentObservationEventType.REWRITE);
        assertThat(captor.getValue().getEventName()).isEqualTo("QueryRewrite");
    }

    @Test
    void noOpRewriteShouldNotRecordEvent() {
        rewriter.rewrite(100L, "trace-9", "帮我分析一下简历", HISTORY);

        verify(observationService, never()).recordEvent(any());
    }
}
