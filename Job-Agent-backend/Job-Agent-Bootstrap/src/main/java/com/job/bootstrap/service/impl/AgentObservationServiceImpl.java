package com.job.bootstrap.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.job.bootstrap.agent.context.AgentRuntimeContext;
import com.job.bootstrap.agent.guardrail.AgentGuardrailService;
import com.job.bootstrap.mapper.AgentObservationEventMapper;
import com.job.bootstrap.observability.AgentObservationRecord;
import com.job.bootstrap.service.AgentObservationService;
import com.job.common.entity.agent.AgentObservationEvent;
import com.job.enums.AgentObservationErrorCategory;
import com.job.enums.AgentObservationEventType;
import com.job.enums.AgentObservationStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Date;
import java.util.UUID;

/**
 * 作者: hfj
 * 功能: Agent 统一观测事件写入服务实现
 * 日期: 2026/6/22
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentObservationServiceImpl implements AgentObservationService {

    private static final int NOT_DELETED = 0;
    private static final int DEFAULT_TEXT_LIMIT = 1000;
    private static final int ERROR_TEXT_LIMIT = 2000;

    private final AgentObservationEventMapper agentObservationEventMapper;
    private final AgentGuardrailService agentGuardrailService;
    private final ObjectMapper objectMapper;

    /**
     * 记录一条观测事件。
     *
     * 设计说明:
     * 1. 观测数据只用于排障和统计，不能因为观测表缺失或写入失败影响 Agent 主流程。
     * 2. 因此本方法内部吞掉异常并写 warn 日志，避免重演“日志字段不匹配导致工具不可用”的问题。
     * 3. 如果调用方没有传 planId/stepId，则从 AgentRuntimeContext 兜底读取当前步骤上下文。
     *
     * @param record 观测事件参数
     */
    @Override
    public void recordEvent(AgentObservationRecord record) {
        if (record == null) {
            return;
        }

        try {
            AgentObservationEvent event = buildEvent(record);
            agentObservationEventMapper.insert(event);
        } catch (Exception exception) {
            log.warn(
                    "Agent 观测事件写入失败，eventType={}, eventName={}, traceId={}, error={}",
                    record.getEventType(),
                    record.getEventName(),
                    record.getTraceId(),
                    exception.getMessage()
            );
        }
    }

    /**
     * 构建数据库实体。
     *
     * 方法步骤:
     * 1. 先补全基础链路字段，保证 traceId/spanId 可用于串联事件。
     * 2. 再补全事件类型、状态、失败分类，方便后台按维度筛选。
     * 3. 最后序列化请求和响应快照，并统一做 PII 脱敏。
     *
     * @param record 原始写入参数
     * @return 数据库实体
     */
    private AgentObservationEvent buildEvent(AgentObservationRecord record) {
        AgentRuntimeContext.Context context = AgentRuntimeContext.get();
        Date now = new Date();

        AgentObservationEvent event = new AgentObservationEvent();
        event.setTraceId(firstText(record.getTraceId(), context == null ? null : context.getTraceId()));
        event.setSpanId(firstText(record.getSpanId(), newSpanId()));
        event.setParentSpanId(record.getParentSpanId());
        event.setUserId(firstLong(record.getUserId(), context == null ? null : context.getUserId()));
        event.setConversationId(firstLong(record.getConversationId(), context == null ? null : context.getConversationId()));
        event.setPlanId(firstLong(record.getPlanId(), context == null ? null : context.getPlanId()));
        event.setStepId(firstLong(record.getStepId(), context == null ? null : context.getStepId()));
        event.setSceneCode(limitText(record.getSceneCode(), DEFAULT_TEXT_LIMIT));
        event.setIntentCode(limitText(firstText(record.getIntentCode(), context == null ? null : context.getIntentCode()), DEFAULT_TEXT_LIMIT));
        event.setEventType(safeEventType(record).name());
        event.setEventName(limitText(record.getEventName(), DEFAULT_TEXT_LIMIT));
        event.setStatus(safeStatus(record).name());
        event.setErrorCategory(resolveErrorCategory(record).name());
        event.setErrorCode(limitText(record.getErrorCode(), DEFAULT_TEXT_LIMIT));
        event.setErrorMsg(limitText(maskText(record.getErrorMsg()), ERROR_TEXT_LIMIT));
        event.setModelCode(limitText(record.getModelCode(), DEFAULT_TEXT_LIMIT));
        event.setToolName(limitText(record.getToolName(), DEFAULT_TEXT_LIMIT));
        event.setInputTokens(record.getInputTokens());
        event.setOutputTokens(record.getOutputTokens());
        event.setTotalTokens(resolveTotalTokens(record));
        event.setTotalCost(record.getTotalCost());
        event.setDurationMs(record.getDurationMs());
        event.setRequestSnapshot(toSnapshotJson(record.getRequestSnapshot()));
        event.setResponseSnapshot(toSnapshotJson(record.getResponseSnapshot()));
        event.setIsDeleted(NOT_DELETED);
        event.setCreateTime(now);
        event.setUpdateTime(now);
        return event;
    }

    /**
     * 推断失败分类。
     *
     * 方法步骤:
     * 1. 成功或跳过事件统一归为 NONE。
     * 2. 调用方明确传入分类时优先使用调用方结果。
     * 3. 调用方没有传时，根据 errorCode、eventType、status 做第一版规则分类。
     *
     * @param record 观测事件参数
     * @return 失败分类
     */
    private AgentObservationErrorCategory resolveErrorCategory(AgentObservationRecord record) {
        AgentObservationStatus status = safeStatus(record);
        if (AgentObservationStatus.SUCCESS.equals(status) || AgentObservationStatus.SKIPPED.equals(status)) {
            return AgentObservationErrorCategory.NONE;
        }
        if (record.getErrorCategory() != null) {
            return record.getErrorCategory();
        }

        String errorCode = record.getErrorCode() == null ? "" : record.getErrorCode();
        String errorMsg = record.getErrorMsg() == null ? "" : record.getErrorMsg().toLowerCase();
        if (errorCode.contains("TOOL_CONFIRMATION_REQUIRED")) {
            return AgentObservationErrorCategory.TOOL_CONFIRMATION;
        }
        if (errorCode.contains("TOOL_PERMISSION_DENIED")) {
            return AgentObservationErrorCategory.PERMISSION_DENIED;
        }
        if (errorCode.contains("TOOL_PARAM_MISSING")) {
            return AgentObservationErrorCategory.PARAM_MISSING;
        }
        if (errorCode.contains("GUARDRAIL") || AgentObservationStatus.BLOCKED.equals(status)) {
            return AgentObservationErrorCategory.GUARDRAIL_BLOCKED;
        }
        if (errorMsg.contains("timeout") || errorMsg.contains("timed out") || errorMsg.contains("超时")) {
            return AgentObservationErrorCategory.TIMEOUT;
        }
        if (AgentObservationEventType.MODEL.equals(safeEventType(record))) {
            return AgentObservationErrorCategory.MODEL_ERROR;
        }
        if (AgentObservationEventType.TOOL.equals(safeEventType(record))) {
            return AgentObservationErrorCategory.TOOL_ERROR;
        }
        return AgentObservationErrorCategory.SYSTEM_ERROR;
    }

    /**
     * 序列化观测快照。
     *
     * @param value 原始快照
     * @return JSON 字符串
     */
    private String toSnapshotJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            Object maskedValue = agentGuardrailService.maskSensitiveData(value);
            return objectMapper.writeValueAsString(maskedValue);
        } catch (Exception exception) {
            return "{}";
        }
    }

    private Integer resolveTotalTokens(AgentObservationRecord record) {
        if (record.getTotalTokens() != null) {
            return record.getTotalTokens();
        }
        if (record.getInputTokens() == null && record.getOutputTokens() == null) {
            return null;
        }
        int inputTokens = record.getInputTokens() == null ? 0 : record.getInputTokens();
        int outputTokens = record.getOutputTokens() == null ? 0 : record.getOutputTokens();
        return inputTokens + outputTokens;
    }

    private AgentObservationEventType safeEventType(AgentObservationRecord record) {
        return record.getEventType() == null ? AgentObservationEventType.TRACE : record.getEventType();
    }

    private AgentObservationStatus safeStatus(AgentObservationRecord record) {
        return record.getStatus() == null ? AgentObservationStatus.SUCCESS : record.getStatus();
    }

    private String firstText(String first, String second) {
        return StringUtils.hasText(first) ? first : second;
    }

    private Long firstLong(Long first, Long second) {
        return first == null ? second : first;
    }

    private String limitText(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String text = value.trim();
        return text.length() <= maxLength ? text : text.substring(0, maxLength);
    }

    private String maskText(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            Object maskedValue = agentGuardrailService.maskSensitiveData(value);
            return maskedValue == null ? null : String.valueOf(maskedValue);
        } catch (Exception exception) {
            return value;
        }
    }

    private String newSpanId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
