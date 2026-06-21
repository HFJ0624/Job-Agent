package com.job.bootstrap.agent.guardrail;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.job.common.agent.tool.AgentToolOutputSchema;
import com.job.common.agent.tool.AgentToolSchema;
import com.job.enums.AgentToolErrorCode;
import com.job.enums.AgentToolValueType;
import com.job.exception.AgentToolException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

/**
 * 作者:hfj
 * 功能:工具输出 JSON 校验
 * 日期:2026/6/21
 */
@Component
@RequiredArgsConstructor
public class ToolOutputJsonValidator {

    private final ObjectMapper objectMapper;

    /**
     * 校验工具输出。
     *
     * 方法步骤:
     * 1. 工具返回必须是合法 JSON，避免 Summary Assistant 基于半截文本或异常堆栈总结。
     * 2. 如果工具 Schema 声明了 outputFields，则校验非 nullable 字段必须存在。
     * 3. 对已声明类型做轻量检查，第一版只判断 JSON 大类，不做复杂业务校验。
     * 4. 校验失败时抛 AgentToolException，让 Executor 把当前步骤标记为 FAILED。
     */
    public void validate(String toolName, AgentToolSchema schema, String outputJson) {
        if (!StringUtils.hasText(outputJson)) {
            throw new AgentToolException(
                    AgentToolErrorCode.TOOL_OUTPUT_INVALID_JSON,
                    toolName,
                    "工具输出为空，无法作为 JSON 使用"
            );
        }

        JsonNode root = readJson(toolName, outputJson);
        if (schema == null || CollectionUtils.isEmpty(schema.getOutputFields()) || !root.isObject()) {
            return;
        }

        for (AgentToolOutputSchema field : schema.getOutputFields()) {
            if (field == null || !StringUtils.hasText(field.getName())) {
                continue;
            }

            JsonNode value = root.get(field.getName());
            if (value == null || value.isNull()) {
                if (!Boolean.TRUE.equals(field.getNullable())) {
                    throw schemaMismatch(toolName, "工具输出缺少必填字段: " + field.getName());
                }
                continue;
            }

            if (!matchesType(value, field.getType())) {
                throw schemaMismatch(toolName, "工具输出字段类型不符合 Schema: " + field.getName());
            }
        }
    }

    private JsonNode readJson(String toolName, String outputJson) {
        try {
            return objectMapper.readTree(outputJson);
        } catch (Exception exception) {
            throw new AgentToolException(
                    AgentToolErrorCode.TOOL_OUTPUT_INVALID_JSON,
                    toolName,
                    "工具输出不是合法 JSON",
                    exception
            );
        }
    }

    private boolean matchesType(JsonNode value, AgentToolValueType type) {
        if (type == null) {
            return true;
        }
        return switch (type) {
            case STRING -> value.isTextual();
            case LONG, INTEGER -> value.isNumber() || value.isTextual();
            case BOOLEAN -> value.isBoolean() || value.isTextual();
            case ARRAY -> value.isArray();
            case OBJECT -> value.isObject();
        };
    }

    private AgentToolException schemaMismatch(String toolName, String message) {
        return new AgentToolException(
                AgentToolErrorCode.TOOL_OUTPUT_SCHEMA_MISMATCH,
                toolName,
                message
        );
    }
}
