package com.job.bootstrap.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.job.bootstrap.agent.context.AgentRuntimeContext;
import com.job.bootstrap.agent.guardrail.AgentGuardrailService;
import com.job.bootstrap.mapper.AgentTraceLogMapper;
import com.job.bootstrap.service.AgentTraceService;
import com.job.common.entity.agent.AgentTraceLog;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * 作者:hfj
 * 功能:Agent Trace 记录服务实现
 * 设计说明:
 * 1. 所有 Agent 对话和工具调用都通过本类落库。
 * 2. 不建议在 AgentChatServiceImpl、Tool 类中重复写 agentTraceLogMapper.insert。
 * 3. 这样做可以保证 trace 日志格式统一，也方便后续扩展 token 成本、模型名称、异常栈等字段。
 * 日期: 2026/6/8 20:13
 */
@Service
@RequiredArgsConstructor
public class AgentTraceServiceImpl implements AgentTraceService {

    private static final int NOT_DELETED = 0;

    private final AgentTraceLogMapper agentTraceLogMapper;
    private final ObjectMapper objectMapper;
    private final AgentGuardrailService agentGuardrailService;

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
     * 判断字符串是否有内容。
     */
    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
