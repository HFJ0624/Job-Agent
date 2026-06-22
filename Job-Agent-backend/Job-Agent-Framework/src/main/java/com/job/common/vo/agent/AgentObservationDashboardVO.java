package com.job.common.vo.agent;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 作者: hfj
 * 功能: Agent 观测看板 VO
 * 日期: 2026/6/22
 */
@Data
public class AgentObservationDashboardVO {

    private Long totalEvents;

    private Long successEvents;

    private Long failedEvents;

    private Long blockedEvents;

    private Long skippedEvents;

    private BigDecimal successRate;

    private Long avgDurationMs;

    private Long totalTokens;

    private BigDecimal totalCost;

    private List<AgentObservationStatItemVO> eventTypeStats;

    private List<AgentObservationStatItemVO> failureStats;

    private List<AgentObservationStatItemVO> slowModelStats;

    private List<AgentObservationStatItemVO> slowToolStats;

    private List<AgentObservationAlertRecordVO> recentAlerts;
}
