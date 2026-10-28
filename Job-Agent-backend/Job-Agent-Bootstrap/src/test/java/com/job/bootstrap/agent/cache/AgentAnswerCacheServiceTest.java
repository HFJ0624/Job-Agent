package com.job.bootstrap.agent.cache;

import com.job.bootstrap.observability.AgentObservationRecord;
import com.job.bootstrap.rag.service.RagEmbeddingService;
import com.job.bootstrap.service.AgentObservationService;
import com.job.enums.AgentObservationEventType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * AgentAnswerCacheService 单元测试（任务 G10）。
 *
 * 覆盖重点:
 * 1. 命中/未命中路径与 CACHE 观测事件；
 * 2. remember 写入后可被 lookup 命中（端到端闭环）；
 * 3. embedding 失败 → lookup 降级为空、remember 静默跳过；
 * 4. 空问题/空答案不写缓存；
 * 5. disabled 时完全不碰 embedding。
 */
class AgentAnswerCacheServiceTest {

    private static final Long USER_ID = 100L;

    private final RagEmbeddingService embeddingService = mock(RagEmbeddingService.class);
    private final AgentObservationService observationService = mock(AgentObservationService.class);

    private AgentCacheProperties properties;
    private AgentAnswerCacheService service;

    @BeforeEach
    void setUp() {
        properties = new AgentCacheProperties();
        properties.setEnabled(true);
        properties.setSimilarityThreshold(0.9);
        properties.setTtlMinutes(30);
        service = new AgentAnswerCacheService(
                properties, new InMemorySemanticCacheStore(properties), embeddingService, observationService);
        // 同一文本 → 同一向量（确定性），不同文本 → 不同向量
        when(embeddingService.embed(anyString())).thenAnswer(invocation -> {
            String text = invocation.getArgument(0);
            java.util.Random random = new java.util.Random(text.hashCode());
            float[] vector = new float[8];
            for (int i = 0; i < vector.length; i++) {
                vector[i] = random.nextFloat();
            }
            return vector;
        });
    }

    @Test
    void rememberThenLookupShouldHit() {
        service.remember(USER_ID, "帮我分析简历", "你的简历得分86，建议补充项目量化数据");

        Optional<String> hit = service.lookup(USER_ID, "帮我分析简历");

        assertThat(hit).isPresent();
        assertThat(hit.get()).isEqualTo("你的简历得分86，建议补充项目量化数据");
    }

    @Test
    void differentQuestionShouldMiss() {
        service.remember(USER_ID, "帮我分析简历", "答案A");

        Optional<String> hit = service.lookup(USER_ID, "推荐几个Java岗位");

        assertThat(hit).isEmpty();
    }

    @Test
    void lookupShouldRecordHitAndMissEvents() {
        service.remember(USER_ID, "帮我分析简历", "答案A");
        observationEvents();

        service.lookup(USER_ID, "帮我分析简历"); // 命中
        service.lookup(USER_ID, "完全不同的问题内容"); // 未命中

        ArgumentCaptor<AgentObservationRecord> captor = ArgumentCaptor.forClass(AgentObservationRecord.class);
        verify(observationService, times(2)).recordEvent(captor.capture());
        assertThat(captor.getAllValues())
                .anyMatch(event -> "CacheHit".equals(event.getEventName()))
                .anyMatch(event -> "CacheMiss".equals(event.getEventName()));
        assertThat(captor.getAllValues().get(0).getEventType()).isEqualTo(AgentObservationEventType.CACHE);
    }

    @Test
    void rememberShouldRecordWriteEvent() {
        service.remember(USER_ID, "帮我分析简历", "答案A");

        ArgumentCaptor<AgentObservationRecord> captor = ArgumentCaptor.forClass(AgentObservationRecord.class);
        verify(observationService, times(1)).recordEvent(captor.capture());
        assertThat(captor.getValue().getEventName()).isEqualTo("CacheWrite");
    }

    @Test
    void embeddingFailureShouldDegradeGracefully() {
        when(embeddingService.embed(anyString())).thenThrow(new RuntimeException("embedding 未配置"));

        assertThat(service.lookup(USER_ID, "帮我分析简历")).isEmpty();
        service.remember(USER_ID, "帮我分析简历", "答案A"); // 不炸

        // 未命中也记了事件（降级路径可见）
        verify(observationService, never()).recordEvent(any());
    }

    @Test
    void blankQuestionOrAnswerShouldNotWrite() {
        service.remember(USER_ID, "  ", "答案A");
        service.remember(USER_ID, "帮我分析简历", "");

        verifyNoInteractions(embeddingService);
    }

    @Test
    void disabledShouldBypassEverything() {
        properties.setEnabled(false);

        assertThat(service.lookup(USER_ID, "帮我分析简历")).isEmpty();
        service.remember(USER_ID, "帮我分析简历", "答案A");

        verifyNoInteractions(embeddingService);
    }

    private void observationEvents() {
        org.mockito.Mockito.clearInvocations(observationService);
    }
}
