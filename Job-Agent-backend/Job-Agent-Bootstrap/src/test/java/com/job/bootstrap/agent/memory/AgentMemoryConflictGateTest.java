package com.job.bootstrap.agent.memory;

import com.job.bootstrap.observability.AgentObservationRecord;
import com.job.bootstrap.service.AgentMemoryService;
import com.job.bootstrap.service.AgentObservationService;
import com.job.common.vo.agent.AgentMemoryVO;
import com.job.enums.AgentMemoryType;
import com.job.enums.AgentObservationEventType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AgentMemoryConflictGate 单元测试（任务 G7）。
 *
 * 覆盖重点:
 * 1. 无历史记忆 → PASS；
 * 2. 同 key 等价值（“上海”vs“上海吧”）→ SKIP_EQUIVALENT；
 * 3. 同 key 不同值（“上海”vs“北京”）→ PASS（交给既有同 key 冲突裁决，门控不越权）；
 * 4. 临时性表达（“今天先不投”）→ DEFER_TEMPORARY；
 * 5. 同类型异 key 值重叠 → CONFLICT_REVIEW；
 * 6. 查询异常 → 降级 PASS（门控故障不挡主流程）；
 * 7. 拦截时记 MEMORY 观测事件。
 */
class AgentMemoryConflictGateTest {

    private final AgentMemoryService agentMemoryService = mock(AgentMemoryService.class);
    private final AgentObservationService agentObservationService = mock(AgentObservationService.class);

    private final AgentMemoryConflictGate gate = new AgentMemoryConflictGate(
            agentMemoryService, agentObservationService);

    private static final Long USER_ID = 100L;

    @BeforeEach
    void setUp() {
        when(agentMemoryService.listActiveMemoriesByType(anyLong(), any())).thenReturn(List.of());
    }

    private AgentMemoryCandidate candidate(String key, String value) {
        return AgentMemoryCandidate.builder()
                .memoryType(AgentMemoryType.USER_PREFERENCE)
                .memoryKey(key)
                .memoryValue(value)
                .confidence(BigDecimal.valueOf(0.9))
                .importance(BigDecimal.valueOf(0.8))
                .build();
    }

    private AgentMemoryVO existingMemory(String key, String value) {
        AgentMemoryVO vo = new AgentMemoryVO();
        vo.setId(1L);
        vo.setMemoryType(AgentMemoryType.USER_PREFERENCE.name());
        vo.setMemoryKey(key);
        vo.setMemoryValue(value);
        vo.setStatus("ACTIVE");
        return vo;
    }

    @Test
    void noHistoryShouldPass() {
        AgentMemoryConflictGate.MemoryGateDecision decision =
                gate.check(USER_ID, "trace-1", candidate("prefer_city", "上海"));

        assertThat(decision.result()).isEqualTo(AgentMemoryConflictGate.GateResult.PASS);
        assertThat(decision.blocked()).isFalse();
    }

    @Test
    void sameKeyEquivalentValueShouldSkip() {
        when(agentMemoryService.listActiveMemoriesByType(USER_ID, AgentMemoryType.USER_PREFERENCE))
                .thenReturn(List.of(existingMemory("prefer_city", "上海")));

        AgentMemoryConflictGate.MemoryGateDecision decision =
                gate.check(USER_ID, "trace-2", candidate("prefer_city", "上海吧"));

        assertThat(decision.result()).isEqualTo(AgentMemoryConflictGate.GateResult.SKIP_EQUIVALENT);
        assertThat(decision.blocked()).isTrue();
    }

    @Test
    void sameKeyDifferentValueShouldPassToExistingArbitration() {
        when(agentMemoryService.listActiveMemoriesByType(USER_ID, AgentMemoryType.USER_PREFERENCE))
                .thenReturn(List.of(existingMemory("prefer_city", "上海")));

        // 同 key 不同值不归门管控——交给 saveOrUpdateMemory 的置信度裁决（任务9）
        AgentMemoryConflictGate.MemoryGateDecision decision =
                gate.check(USER_ID, "trace-3", candidate("prefer_city", "北京"));

        assertThat(decision.result()).isEqualTo(AgentMemoryConflictGate.GateResult.PASS);
    }

    @Test
    void temporaryExpressionShouldDefer() {
        AgentMemoryConflictGate.MemoryGateDecision decision =
                gate.check(USER_ID, "trace-4", candidate("current_status", "今天先不投递了"));

        assertThat(decision.result()).isEqualTo(AgentMemoryConflictGate.GateResult.DEFER_TEMPORARY);
        assertThat(decision.blocked()).isTrue();
    }

    @Test
    void differentKeyOverlappingValueShouldConflictReview() {
        when(agentMemoryService.listActiveMemoriesByType(USER_ID, AgentMemoryType.USER_PREFERENCE))
                .thenReturn(List.of(existingMemory("target_position", "Java开发工程师")));

        AgentMemoryConflictGate.MemoryGateDecision decision =
                gate.check(USER_ID, "trace-5", candidate("job_title", "Java开发"));

        assertThat(decision.result()).isEqualTo(AgentMemoryConflictGate.GateResult.CONFLICT_REVIEW);
        assertThat(decision.reason()).contains("target_position");
    }

    @Test
    void shortValueShouldNotTriggerOverlap() {
        // 短值（“上海”）不做包含判定，避免被“上海浦东张江”误拦
        when(agentMemoryService.listActiveMemoriesByType(USER_ID, AgentMemoryType.USER_PREFERENCE))
                .thenReturn(List.of(existingMemory("work_area", "上海浦东张江")));

        AgentMemoryConflictGate.MemoryGateDecision decision =
                gate.check(USER_ID, "trace-6", candidate("prefer_city", "上海"));

        assertThat(decision.result()).isEqualTo(AgentMemoryConflictGate.GateResult.PASS);
    }

    @Test
    void queryFailureShouldDegradeToPass() {
        when(agentMemoryService.listActiveMemoriesByType(anyLong(), any()))
                .thenThrow(new RuntimeException("数据库连接失败"));

        AgentMemoryConflictGate.MemoryGateDecision decision =
                gate.check(USER_ID, "trace-7", candidate("prefer_city", "上海"));

        assertThat(decision.result()).isEqualTo(AgentMemoryConflictGate.GateResult.PASS);
    }

    @Test
    void blockedDecisionShouldRecordMemoryObservationEvent() {
        when(agentMemoryService.listActiveMemoriesByType(USER_ID, AgentMemoryType.USER_PREFERENCE))
                .thenReturn(List.of(existingMemory("prefer_city", "上海")));

        gate.check(USER_ID, "trace-8", candidate("prefer_city", "上海吧"));

        org.mockito.ArgumentCaptor<AgentObservationRecord> captor =
                org.mockito.ArgumentCaptor.forClass(AgentObservationRecord.class);
        verify(agentObservationService, times(1)).recordEvent(captor.capture());
        assertThat(captor.getValue().getEventType()).isEqualTo(AgentObservationEventType.MEMORY);
        assertThat(captor.getValue().getEventName()).isEqualTo("MemoryGateBlock");
        assertThat((java.util.Map<String, Object>) captor.getValue().getRequestSnapshot()).containsKey("decision");
    }

    @Test
    void passDecisionShouldNotRecordEvent() {
        gate.check(USER_ID, "trace-9", candidate("prefer_city", "上海"));

        verify(agentObservationService, never()).recordEvent(any());
    }
}
