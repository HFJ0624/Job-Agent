package com.job.bootstrap.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.job.bootstrap.mapper.AgentTraceLogMapper;
import com.job.bootstrap.service.AgentTraceService;
import com.job.common.entity.agent.AgentTraceLog;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * 作者:hfj
 * 功能:Agent Trace 记录服务实现
 * 日期: 2026/6/8 20:13
 */
@Service
@RequiredArgsConstructor
public class AgentTraceServiceImpl implements AgentTraceService {

    private static final int NOT_DELETED = 0;

    private final AgentTraceLogMapper agentTraceLogMapper;
    private final ObjectMapper objectMapper;

    /**
     * 保存工具调用 Trace。
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
        AgentTraceLog log = new AgentTraceLog();
        log.setTraceId(UUID.randomUUID().toString().replace("-", ""));
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
     * 对象转 JSON。
     */
    private String toJson(Object value) {
        if (value == null) {
            return null;
        }

        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return "{}";
        }
    }
}
