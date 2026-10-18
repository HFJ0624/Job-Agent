package com.job.bootstrap.service.impl;

import com.job.bootstrap.agent.memory.AgentMemoryActionClassifier;
import com.job.bootstrap.agent.memory.AgentMemoryActionDecision;
import com.job.bootstrap.agent.memory.AgentMemoryCandidate;
import com.job.bootstrap.agent.memory.AgentMemoryConflictGate;
import com.job.bootstrap.agent.memory.AgentMemoryLlmExtractor;
import com.job.bootstrap.agent.memory.AgentMemoryRuleExtractor;
import com.job.bootstrap.agent.memory.AgentMemoryWritePolicy;
import com.job.bootstrap.service.AgentMemoryContextService;
import com.job.bootstrap.service.AgentMemoryService;
import com.job.common.entity.agent.AgentLongTermMemory;
import com.job.common.vo.agent.AgentMemoryVO;
import com.job.enums.AgentMemoryActionType;
import com.job.enums.AgentMemoryType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

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
 * AgentMemoryCaptureServiceImpl 门控接线测试（任务 G7）。
 *
 * 覆盖重点:
 * 1. 门控拦截（SKIP_EQUIVALENT）→ 不落库、不触发画像重建；
 * 2. 门控放行（PASS）→ 正常落库并触发画像重建；
 * 3. 非 SET/UPDATE 动作（NORMAL_CHAT）→ 不进入门控与写入。
 */
class AgentMemoryCaptureServiceImplTest {

    private static final Long USER_ID = 100L;

    private final AgentMemoryActionClassifier actionClassifier = mock(AgentMemoryActionClassifier.class);
    private final AgentMemoryRuleExtractor ruleExtractor = mock(AgentMemoryRuleExtractor.class);
    private final AgentMemoryLlmExtractor llmExtractor = mock(AgentMemoryLlmExtractor.class);
    private final AgentMemoryWritePolicy writePolicy = mock(AgentMemoryWritePolicy.class);
    private final AgentMemoryConflictGate conflictGate = mock(AgentMemoryConflictGate.class);
    private final AgentMemoryService agentMemoryService = mock(AgentMemoryService.class);
    private final AgentMemoryContextService agentMemoryContextService = mock(AgentMemoryContextService.class);

    private final AgentMemoryCaptureServiceImpl service = new AgentMemoryCaptureServiceImpl(
            actionClassifier, ruleExtractor, llmExtractor, writePolicy,
            conflictGate, agentMemoryService, agentMemoryContextService);

    @BeforeEach
    void setUp() {
        when(llmExtractor.extract(anyLong(), anyString(), anyString())).thenReturn(List.of());
        when(writePolicy.allowWrite(any())).thenReturn(true);
        when(agentMemoryService.saveOrUpdateMemory(
                anyLong(), any(), anyString(), anyString(), any(), anyString(), anyLong(), any(), any()))
                .thenReturn(new AgentLongTermMemory());
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

    private void stubSetAction() {
        when(actionClassifier.classify(anyString())).thenReturn(AgentMemoryActionDecision.builder()
                .actionType(AgentMemoryActionType.SET_MEMORY)
                .reason("识别到偏好表达")
                .build());
    }

    @Test
    void gateBlockedShouldNotSaveMemory() {
        stubSetAction();
        when(ruleExtractor.extract(anyString())).thenReturn(List.of(candidate("prefer_city", "上海吧")));
        when(conflictGate.check(anyLong(), anyString(), any())).thenReturn(
                new AgentMemoryConflictGate.MemoryGateDecision(
                        AgentMemoryConflictGate.GateResult.SKIP_EQUIVALENT, "等价值已存在"));

        List<AgentMemoryVO> saved = service.captureFromUserMessage(USER_ID, 10L, "trace-1", "我喜欢上海吧");

        assertThat(saved).isEmpty();
        verify(agentMemoryService, never()).saveOrUpdateMemory(
                anyLong(), any(), anyString(), anyString(), any(), anyString(), anyLong(), any(), any());
        verify(agentMemoryContextService, never()).rebuildProfile(anyLong());
    }

    @Test
    void gatePassShouldSaveMemoryAndRebuildProfile() {
        stubSetAction();
        when(ruleExtractor.extract(anyString())).thenReturn(List.of(candidate("prefer_city", "上海")));
        when(conflictGate.check(anyLong(), anyString(), any())).thenReturn(
                AgentMemoryConflictGate.MemoryGateDecision.PASS);

        List<AgentMemoryVO> saved = service.captureFromUserMessage(USER_ID, 10L, "trace-2", "我喜欢上海");

        assertThat(saved).hasSize(1);
        verify(agentMemoryService, times(1)).saveOrUpdateMemory(
                eq(USER_ID), eq(AgentMemoryType.USER_PREFERENCE), eq("prefer_city"), eq("上海"),
                any(), anyString(), anyLong(), any(), any());
        verify(agentMemoryContextService, times(1)).rebuildProfile(USER_ID);
    }

    @Test
    void normalChatShouldNotEnterGate() {
        when(actionClassifier.classify(anyString())).thenReturn(AgentMemoryActionDecision.builder()
                .actionType(AgentMemoryActionType.NORMAL_CHAT)
                .reason("普通对话")
                .build());

        service.captureFromUserMessage(USER_ID, 10L, "trace-3", "今天天气怎么样");

        verify(ruleExtractor, never()).extract(anyString());
        verify(conflictGate, never()).check(anyLong(), anyString(), any());
        verify(agentMemoryService, never()).saveOrUpdateMemory(
                anyLong(), any(), anyString(), anyString(), any(), anyString(), anyLong(), any(), any());
    }
}
