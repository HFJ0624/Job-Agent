package com.job.common.vo.agent;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.job.common.entity.agent.AgentObservationEvent;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 作者: hfj
 * 功能: Agent 统一观测事件展示 VO
 * 日期: 2026/6/22
 */
@Data
public class AgentObservationEventVO {

    private Long id;

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

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    public static AgentObservationEventVO from(AgentObservationEvent entity) {
        if (entity == null) {
            return null;
        }

        AgentObservationEventVO vo = new AgentObservationEventVO();
        vo.setId(entity.getId());
        vo.setTraceId(entity.getTraceId());
        vo.setSpanId(entity.getSpanId());
        vo.setParentSpanId(entity.getParentSpanId());
        vo.setUserId(entity.getUserId());
        vo.setConversationId(entity.getConversationId());
        vo.setPlanId(entity.getPlanId());
        vo.setStepId(entity.getStepId());
        vo.setSceneCode(entity.getSceneCode());
        vo.setIntentCode(entity.getIntentCode());
        vo.setEventType(entity.getEventType());
        vo.setEventName(entity.getEventName());
        vo.setStatus(entity.getStatus());
        vo.setErrorCategory(entity.getErrorCategory());
        vo.setErrorCode(entity.getErrorCode());
        vo.setErrorMsg(entity.getErrorMsg());
        vo.setModelCode(entity.getModelCode());
        vo.setToolName(entity.getToolName());
        vo.setInputTokens(entity.getInputTokens());
        vo.setOutputTokens(entity.getOutputTokens());
        vo.setTotalTokens(entity.getTotalTokens());
        vo.setTotalCost(entity.getTotalCost());
        vo.setDurationMs(entity.getDurationMs());
        vo.setRequestSnapshot(entity.getRequestSnapshot());
        vo.setResponseSnapshot(entity.getResponseSnapshot());
        vo.setCreateTime(entity.getCreateTime());
        return vo;
    }
}
