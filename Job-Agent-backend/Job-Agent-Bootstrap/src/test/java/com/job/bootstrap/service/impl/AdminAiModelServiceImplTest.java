package com.job.bootstrap.service.impl;

import com.job.bootstrap.mapper.AiModelCallLogMapper;
import com.job.bootstrap.mapper.AiModelConfigMapper;
import com.job.bootstrap.mapper.AiModelRouteMapper;
import com.job.common.dto.ai.AiModelCallLogQueryDTO;
import com.job.common.vo.ai.AiModelCostStatsVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AdminAiModelServiceImpl 成本统计 SQL 化测试（任务8 第④项）。
 *
 * 覆盖重点:
 * 1. costStats 走数据库聚合（selectMaps），不再全表 selectList 拉内存。
 * 2. 聚合行正确映射为 VO（total/success/failed/tokens/cost/avgCostTime）。
 * 3. 空结果集 / null 聚合值时兜底为 0，不抛异常。
 */
class AdminAiModelServiceImplTest {

    private AiModelConfigMapper configMapper;
    private AiModelRouteMapper routeMapper;
    private AiModelCallLogMapper callLogMapper;
    private AdminAiModelServiceImpl service;

    @BeforeEach
    void setUp() {
        configMapper = mock(AiModelConfigMapper.class);
        routeMapper = mock(AiModelRouteMapper.class);
        callLogMapper = mock(AiModelCallLogMapper.class);
        service = new AdminAiModelServiceImpl(configMapper, routeMapper, callLogMapper);
    }

    @Test
    void costStatsShouldAggregateViaSql() {
        when(callLogMapper.selectMaps(any())).thenReturn(List.of(aggregateRow(
                10L, 7L, 1000L, new BigDecimal("0.5"), new BigDecimal("250.5"))));

        AiModelCostStatsVO stats = service.costStats(new AiModelCallLogQueryDTO());

        assertThat(stats.getTotalCalls()).isEqualTo(10L);
        assertThat(stats.getSuccessCalls()).isEqualTo(7L);
        assertThat(stats.getFailedCalls()).isEqualTo(3L);
        assertThat(stats.getTotalTokens()).isEqualTo(1000L);
        assertThat(stats.getTotalCost()).isEqualByComparingTo(new BigDecimal("0.500000"));
        assertThat(stats.getAvgCostTime()).isEqualTo(250L);
        // 关键：走 SQL 聚合，不再全表拉取
        verify(callLogMapper, never()).selectList(any());
    }

    @Test
    void costStatsEmptyResultShouldReturnZeros() {
        when(callLogMapper.selectMaps(any())).thenReturn(List.of());

        AiModelCostStatsVO stats = service.costStats(new AiModelCallLogQueryDTO());

        assertThat(stats.getTotalCalls()).isZero();
        assertThat(stats.getSuccessCalls()).isZero();
        assertThat(stats.getFailedCalls()).isZero();
        assertThat(stats.getTotalTokens()).isZero();
        assertThat(stats.getAvgCostTime()).isZero();
        assertThat(stats.getTotalCost()).isEqualByComparingTo(new BigDecimal("0.000000"));
    }

    @Test
    void costStatsNullAggregateValuesShouldFallbackToZero() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("totalCalls", 5L);
        row.put("successCalls", null); // 极端情况：聚合值为 null
        row.put("totalTokens", null);
        row.put("totalCost", null);
        row.put("avgCostTime", null);
        when(callLogMapper.selectMaps(any())).thenReturn(List.of(row));

        AiModelCostStatsVO stats = service.costStats(new AiModelCallLogQueryDTO());

        assertThat(stats.getTotalCalls()).isEqualTo(5L);
        assertThat(stats.getSuccessCalls()).isZero();
        assertThat(stats.getFailedCalls()).isEqualTo(5L); // 5 - 0
        assertThat(stats.getTotalCost()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    // 模拟 MySQL 聚合行：COUNT/BIGINT → Long，SUM(total_cost)/AVG → BigDecimal
    private Map<String, Object> aggregateRow(Long totalCalls, Long successCalls, Long totalTokens,
                                             BigDecimal totalCost, BigDecimal avgCostTime) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("totalCalls", totalCalls);
        row.put("successCalls", successCalls);
        row.put("totalTokens", totalTokens);
        row.put("totalCost", totalCost);
        row.put("avgCostTime", avgCostTime);
        return row;
    }
}
