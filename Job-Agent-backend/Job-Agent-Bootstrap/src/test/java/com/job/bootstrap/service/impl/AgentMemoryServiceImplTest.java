package com.job.bootstrap.service.impl;

import com.job.bootstrap.agent.memory.AgentMemoryProperties;
import com.job.bootstrap.mapper.AgentLongTermMemoryMapper;
import com.job.bootstrap.mapper.AgentMemoryHistoryMapper;
import com.job.common.entity.agent.AgentLongTermMemory;
import com.job.common.entity.agent.AgentMemoryHistory;
import com.job.enums.AgentMemoryStatus;
import com.job.enums.AgentMemoryType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AgentMemoryServiceImpl 治理测试（任务9）。
 *
 * 覆盖重点:
 * 1. 冲突裁决：低置信度来源不覆盖高置信度记忆（保留旧值 + needConfirm=1 + 记冲突历史）；
 *    高置信度来源覆盖并清除待确认标记。
 * 2. TTL：新增记忆写入 expire_time。
 * 3. 写放大修复：召回只走单列 update(last_used_time)，不再 updateById 整实体。
 * 4. 归档：过期 ACTIVE 记忆被置为 ARCHIVED 并记历史。
 */
class AgentMemoryServiceImplTest {

    private AgentLongTermMemoryMapper agentLongTermMemoryMapper;
    private AgentMemoryHistoryMapper agentMemoryHistoryMapper;
    private AgentMemoryProperties properties;
    private AgentMemoryServiceImpl service;

    @BeforeEach
    void setUp() {
        agentLongTermMemoryMapper = mock(AgentLongTermMemoryMapper.class);
        agentMemoryHistoryMapper = mock(AgentMemoryHistoryMapper.class);
        properties = new AgentMemoryProperties();
        service = new AgentMemoryServiceImpl(agentLongTermMemoryMapper, agentMemoryHistoryMapper, properties);
    }

    @Test
    void lowerConfidenceSourceShouldNotOverwrite() {
        AgentLongTermMemory existing = mem(AgentMemoryType.USER_PREFERENCE, "preferred_city", "北京", "USER_MESSAGE");
        when(agentLongTermMemoryMapper.selectOne(any())).thenReturn(existing);

        AgentLongTermMemory result = service.saveOrUpdateMemory(
                100L, AgentMemoryType.USER_PREFERENCE, "preferred_city", "上海", "摘要", "TOOL_RESULT", null, null, null);

        assertThat(result.getMemoryValue()).isEqualTo("北京"); // 旧值保留，未被低置信度覆盖
        assertThat(result.getNeedConfirm()).isEqualTo(1);      // 标记待确认
        verify(agentLongTermMemoryMapper).updateById(any(AgentLongTermMemory.class));   // 持久化 needConfirm
        verify(agentLongTermMemoryMapper, never()).insert(any(AgentLongTermMemory.class));
        verify(agentMemoryHistoryMapper).insert(any(AgentMemoryHistory.class));        // 记冲突历史
    }

    @Test
    void higherConfidenceSourceShouldOverwrite() {
        AgentLongTermMemory existing = mem(AgentMemoryType.USER_PREFERENCE, "preferred_city", "北京", "TOOL_RESULT");
        when(agentLongTermMemoryMapper.selectOne(any())).thenReturn(existing);

        AgentLongTermMemory result = service.saveOrUpdateMemory(
                100L, AgentMemoryType.USER_PREFERENCE, "preferred_city", "上海", "摘要", "USER_MESSAGE", null, null, null);

        assertThat(result.getMemoryValue()).isEqualTo("上海"); // 高置信度覆盖
        assertThat(result.getNeedConfirm()).isEqualTo(0);      // 清除待确认
        verify(agentLongTermMemoryMapper).updateById(any(AgentLongTermMemory.class));
    }

    @Test
    void insertShouldSetExpireTime() {
        when(agentLongTermMemoryMapper.selectOne(any())).thenReturn(null); // 无旧记忆 → 新增

        service.saveOrUpdateMemory(
                100L, AgentMemoryType.USER_PREFERENCE, "preferred_city", "杭州", "摘要", "USER_MESSAGE", null, null, null);

        ArgumentCaptor<AgentLongTermMemory> captor = ArgumentCaptor.forClass(AgentLongTermMemory.class);
        verify(agentLongTermMemoryMapper).insert(captor.capture());
        assertThat(captor.getValue().getExpireTime()).isNotNull(); // TTL 已设置
        assertThat(captor.getValue().getNeedConfirm()).isEqualTo(0);
    }

    @Test
    void recallShouldOnlyUpdateLastUsedTimeColumn() {
        AgentLongTermMemory memory = mem(AgentMemoryType.USER_PREFERENCE, "preferred_city", "杭州", "USER_MESSAGE");
        memory.setImportance(new BigDecimal("0.90"));
        when(agentLongTermMemoryMapper.selectList(any())).thenReturn(List.of(memory));

        service.searchMemories(100L, "城市", 5);

        // 只走单列 update(last_used_time)，不再 updateById 整实体 → 修复读引发写放大
        verify(agentLongTermMemoryMapper).update(any(), any());
        verify(agentLongTermMemoryMapper, never()).updateById(any(AgentLongTermMemory.class));
    }

    @Test
    void shouldArchiveExpiredMemories() {
        AgentLongTermMemory expired = mem(AgentMemoryType.USER_PREFERENCE, "preferred_city", "杭州", "USER_MESSAGE");
        expired.setStatus(AgentMemoryStatus.ACTIVE.name());
        expired.setExpireTime(new Date(System.currentTimeMillis() - 86_400_000L)); // 昨天已过期
        when(agentLongTermMemoryMapper.selectList(any())).thenReturn(List.of(expired));

        int archived = service.archiveExpiredMemories();

        assertThat(archived).isEqualTo(1);
        assertThat(expired.getStatus()).isEqualTo(AgentMemoryStatus.ARCHIVED.name());
        verify(agentLongTermMemoryMapper).updateById(expired);
        verify(agentMemoryHistoryMapper).insert(any(AgentMemoryHistory.class));
    }

    private AgentLongTermMemory mem(AgentMemoryType type, String key, String value, String sourceType) {
        AgentLongTermMemory memory = new AgentLongTermMemory();
        memory.setId(1L);
        memory.setUserId(100L);
        memory.setMemoryType(type.name());
        memory.setMemoryKey(key);
        memory.setMemoryValue(value);
        memory.setSummary(value);
        memory.setSourceType(sourceType);
        memory.setConfidence(new BigDecimal("0.80"));
        memory.setImportance(new BigDecimal("0.50"));
        memory.setStatus(AgentMemoryStatus.ACTIVE.name());
        memory.setNeedConfirm(0);
        memory.setIsDeleted(0);
        return memory;
    }
}
