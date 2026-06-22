package com.job.common.entity.agent;

import com.baomidou.mybatisplus.annotation.TableName;
import com.job.common.entity.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 作者: hfj
 * 功能: Agent 统一观测事件实体
 * 日期: 2026/6/22
 *
 * 说明:
 * 1. 该表不替代 agent_trace_log 和 ai_model_call_log，而是把模型、工具、Executor 等日志汇总成同一视图。
 * 2. 第一版先做查询与排障闭环，后续告警、看板、Trace 保留策略都可以基于这张表扩展。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("agent_observation_event")
public class AgentObservationEvent extends BaseEntity {

    private String traceId;

    private String spanId;

    private String parentSpanId;

    private Long userId;

    private Long conversationId;

    private Long planId;

    private Long stepId;

    private String sceneCode;

    private String intentCode;

    private String eventType;

    private String eventName;

    private String status;

    private String errorCategory;

    private String errorCode;

    private String errorMsg;

    private String modelCode;

    private String toolName;

    private Integer inputTokens;

    private Integer outputTokens;

    private Integer totalTokens;

    private BigDecimal totalCost;

    private Long durationMs;

    private String requestSnapshot;

    private String responseSnapshot;
}
