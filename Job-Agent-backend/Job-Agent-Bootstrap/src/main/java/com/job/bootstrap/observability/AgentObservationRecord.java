package com.job.bootstrap.observability;

import com.job.enums.AgentObservationErrorCategory;
import com.job.enums.AgentObservationEventType;
import com.job.enums.AgentObservationStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 作者: hfj
 * 功能: Agent 统一观测事件写入参数
 * 日期: 2026/6/22
 */
@Data
@Builder
public class AgentObservationRecord {

    private String traceId;

    private String spanId;

    private String parentSpanId;

    private Long userId;

    private Long conversationId;

    private Long planId;

    private Long stepId;

    private String sceneCode;

    private String intentCode;

    private AgentObservationEventType eventType;

    private String eventName;

    private AgentObservationStatus status;

    private AgentObservationErrorCategory errorCategory;

    private String errorCode;

    private String errorMsg;

    private String modelCode;

    private String toolName;

    private Integer inputTokens;

    private Integer outputTokens;

    private Integer totalTokens;

    private BigDecimal totalCost;

    private Long durationMs;

    private Object requestSnapshot;

    private Object responseSnapshot;
}
