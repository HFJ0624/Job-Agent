package com.job.bootstrap.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.job.bootstrap.agent.context.AgentRuntimeContext;
import com.job.bootstrap.agent.guardrail.AgentGuardrailService;
import com.job.bootstrap.mapper.AgentTraceLogMapper;
import com.job.bootstrap.observability.AgentObservationRecord;
import com.job.bootstrap.service.AgentObservationService;
import com.job.bootstrap.service.AgentTraceService;
import com.job.common.entity.agent.AgentTraceLog;
import com.job.enums.AgentObservationEventType;
import com.job.enums.AgentObservationStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Agent Trace 记录服务实现，是 Agent 主链路与工具调用的统一 Trace 落库入口。
 *
 * <p>核心职责：
 * 1. 提供 saveTrace / saveToolTrace 两个入口，统一写入 agent_trace_log 表。
 * 2. 对 input/output 做 PII 脱敏，避免 Trace 暴露手机号、邮箱、token。
 * 3. 同步写入 AgentObservationService，把 Trace 转换为 Observation 事件，
 *    方便后台按 TRACE/TOOL/GUARDRAIL 三类事件筛选。</p>
 *
 * <p>所属业务模块：Job-Agent-Bootstrap 模块下的 Agent Trace Service 层。</p>
 *
 * <p>主要调用链：
 * AgentChatServiceImpl (主链路 Trace) -> AgentTraceServiceImpl.saveTrace
 * AgentToolInvoker / 各 Tool (工具 Trace) -> AgentTraceServiceImpl.saveToolTrace
 * -> AgentGuardrailService.maskSensitiveData (脱敏)
 * -> agent_trace_log 落库
 * -> AgentObservationService.recordEvent (同步写 Observation)</p>
 *
 * <p>与其他核心组件的关系：
 * <ul>
 *   <li>AgentRuntimeContext 通过 ThreadLocal 暴露 traceId，工具内部 saveToolTrace 自动复用；</li>
 *   <li>AgentGuardrailService.maskSensitiveData 对 input/output 做统一脱敏；</li>
 *   <li>AgentObservationService 接收转换后的 Observation 事件，与 Trace 形成互补。</li>
 * </ul></p>
 *
 * <p>设计说明:
 * 1. 所有 Agent 对话和工具调用都通过本类落库。
 * 2. 不建议在 AgentChatServiceImpl、Tool 类中重复写 agentTraceLogMapper.insert。
 * 3. 这样做可以保证 trace 日志格式统一，也方便后续扩展 token 成本、模型名称、异常栈等字段。</p>
 *
 * 作者:hfj
 * 日期: 2026/6/8 20:13
 */
@Service
@RequiredArgsConstructor
public class AgentTraceServiceImpl implements AgentTraceService {

    private static final int NOT_DELETED = 0;

    private final AgentTraceLogMapper agentTraceLogMapper;
    private final ObjectMapper objectMapper;
    private final AgentGuardrailService agentGuardrailService;
    private final AgentObservationService agentObservationService;

    /**
     * 保存一条 Agent Trace。
     */
    @Override
    public void saveTrace(
            String traceId,
            Long userId,
            Long conversationId,
            String intentCode,
            String toolName,
            Object input,
            Object output,
            String status,
            String errorMsg,
            Long costTime
    ) {
        AgentTraceLog log = new AgentTraceLog();

        /*
         * traceId 为空时自动生成。
         * 普通情况下，AgentChatServiceImpl 会生成主 traceId 并传入。
         */
        log.setTraceId(hasText(traceId) ? traceId : newTraceId());

        log.setUserId(userId);
        log.setConversationId(conversationId);
        log.setIntentCode(intentCode);
        log.setToolName(toolName);
        log.setInputData(toJson(input));
        log.setOutputData(toJson(output));
        log.setStatus(status);
        log.setErrorMsg(errorMsg);
        log.setCostTime(costTime);
        log.setIsDeleted(NOT_DELETED);

        agentTraceLogMapper.insert(log);
        recordObservationEvent(log, input, output);
    }

    /**
     * 保存工具调用 Trace。
     *
     * 说明:
     * 1. 工具类一般不直接生成 traceId。
     * 2. 工具类从 AgentRuntimeContext 中获取当前 traceId。
     * 3. 这样同一轮对话中的多个工具调用可以被串到同一条链路下。
     */
    @Override
    public void saveToolTrace(
            Long userId,
            Long conversationId,
            String intentCode,
            String toolName,
            Object input,
            Object output,
            String status,
            String errorMsg,
            Long costTime
    ) {
        String traceId = AgentRuntimeContext.getTraceId();

        saveTrace(
                traceId,
                userId,
                conversationId,
                intentCode,
                toolName,
                input,
                output,
                status,
                errorMsg,
                costTime
        );
    }

    /**
     * 对象转 JSON。
     *
     * 说明:
     * 1. Trace 的 input/output 建议统一存 JSON 字符串。
     * 2. 这样后台页面可以直接格式化展示。
     * 3. 序列化失败时返回 "{}"，避免因为日志失败影响主业务。
     */
    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            /*
             * Trace 是后台排查用数据，不应该保存明文手机号、邮箱、身份证、token、密码。
             * 这里统一做脱敏，避免每个 Tool、ChatService、Executor 各自重复处理。
             */
            Object maskedValue = agentGuardrailService.maskSensitiveData(value);
            return objectMapper.writeValueAsString(maskedValue);
        } catch (Exception e) {
            return "{}";
        }
    }

    /**
     * 生成新的 traceId。
     */
    private String newTraceId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 同步写入统一观测事件。
     *
     * 方法步骤:
     * 1. toolName 不为空时记录为 TOOL 事件，表示真实工具调用。
     * 2. status 为 BLOCKED 时记录为 GUARDRAIL 事件，方便后台单独筛出安全拦截。
     * 3. 其他 Agent 主流程日志记录为 TRACE 事件，用于补齐完整链路。
     *
     * @param log Trace 日志实体
     * @param input 原始入参
     * @param output 原始出参
     */
    private void recordObservationEvent(AgentTraceLog log, Object input, Object output) {
        agentObservationService.recordEvent(AgentObservationRecord.builder()
                .traceId(log.getTraceId())
                .userId(log.getUserId())
                .conversationId(log.getConversationId())
                .intentCode(log.getIntentCode())
                .eventType(resolveEventType(log))
                .eventName(resolveEventName(log))
                .status(resolveStatus(log.getStatus()))
                .errorMsg(log.getErrorMsg())
                .toolName(log.getToolName())
                .durationMs(log.getCostTime())
                .requestSnapshot(input)
                .responseSnapshot(output)
                .build());
    }

    private AgentObservationEventType resolveEventType(AgentTraceLog log) {
        if ("BLOCKED".equals(log.getStatus())) {
            return AgentObservationEventType.GUARDRAIL;
        }
        return hasText(log.getToolName()) ? AgentObservationEventType.TOOL : AgentObservationEventType.TRACE;
    }

    private String resolveEventName(AgentTraceLog log) {
        if (hasText(log.getToolName())) {
            return log.getToolName();
        }
        if (hasText(log.getIntentCode())) {
            return log.getIntentCode();
        }
        return "AgentTrace";
    }

    private AgentObservationStatus resolveStatus(String status) {
        if ("FAILED".equals(status)) {
            return AgentObservationStatus.FAILED;
        }
        if ("BLOCKED".equals(status)) {
            return AgentObservationStatus.BLOCKED;
        }
        if ("SKIPPED".equals(status)) {
            return AgentObservationStatus.SKIPPED;
        }
        return AgentObservationStatus.SUCCESS;
    }

    /**
     * 判断字符串是否有内容。
     */
    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
