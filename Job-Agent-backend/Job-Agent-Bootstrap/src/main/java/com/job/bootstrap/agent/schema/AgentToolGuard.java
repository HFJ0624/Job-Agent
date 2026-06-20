package com.job.bootstrap.agent.schema;

import com.job.bootstrap.agent.context.AgentRuntimeContext;
import com.job.common.agent.tool.AgentToolParamSchema;
import com.job.common.agent.tool.AgentToolSchema;
import com.job.enums.AgentToolErrorCode;
import com.job.enums.AgentToolPermissionType;
import com.job.exception.AgentToolException;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 作者:hfj
 * 功能:Agent 工具执行前统一校验
 * 日期:2026/6/20
 */
@Component
public class AgentToolGuard {

    private final AgentToolSchemaRegistry schemaRegistry;

    public AgentToolGuard(AgentToolSchemaRegistry schemaRegistry) {
        this.schemaRegistry = schemaRegistry;
    }

    /**
     * 校验工具是否允许执行。
     *
     * @param toolName 工具名
     * @param input 工具入参
     * @return 工具 Schema
     */
    public AgentToolSchema validate(String toolName, Map<String, Object> input) {
        AgentToolSchema schema = schemaRegistry.getRequired(toolName);
        AgentRuntimeContext.Context context = getContext(toolName);

        validatePermission(schema, context);
        validateRequiredParams(schema, input);
        validateConfirmation(schema, context);

        return schema;
    }

    /**
     * 构造统一 Trace input。
     *
     * 说明:
     * 1. 原始工具入参放在 input 字段。
     * 2. 权限、副作用、确认策略放在 toolSchema 字段。
     * 3. 后台 Trace 页面可以直接看出“模型调了什么工具、是否有副作用、是否已确认”。
     */
    public Map<String, Object> buildTraceInput(
            String toolName,
            AgentToolSchema schema,
            Map<String, Object> input
    ) {
        Map<String, Object> traceInput = new LinkedHashMap<>();
        traceInput.put("toolName", toolName);
        traceInput.put("input", input);

        AgentRuntimeContext.Context context = AgentRuntimeContext.get();
        if (context != null) {
            /*
             * planId/stepId 只由 Executor 设置。
             * 如果工具不是由 Executor 调用，这两个字段就是 null，Trace 仍然兼容旧链路。
             */
            traceInput.put("planId", context.getPlanId());
            traceInput.put("stepId", context.getStepId());
        }

        if (schema != null) {
            Map<String, Object> schemaSnapshot = new LinkedHashMap<>();
            schemaSnapshot.put("displayName", schema.getDisplayName());
            schemaSnapshot.put("permissionType", schema.getPermissionType());
            schemaSnapshot.put("sideEffectType", schema.getSideEffectType());
            schemaSnapshot.put("hasSideEffect", schema.getHasSideEffect());
            schemaSnapshot.put("requiresUserConfirmation", schema.getRequiresUserConfirmation());
            schemaSnapshot.put("confirmed", !Boolean.TRUE.equals(schema.getRequiresUserConfirmation())
                    || AgentRuntimeContext.isToolConfirmed(toolName));
            schemaSnapshot.put("confirmationMessage", schema.getConfirmationMessage());
            schemaSnapshot.put("inputParams", schema.getInputParams());
            schemaSnapshot.put("errorCodes", schema.getErrorCodes());
            traceInput.put("toolSchema", schemaSnapshot);
        }

        return traceInput;
    }

    private AgentRuntimeContext.Context getContext(String toolName) {
        try {
            return AgentRuntimeContext.getRequired();
        } catch (Exception exception) {
            throw new AgentToolException(
                    AgentToolErrorCode.TOOL_CONTEXT_MISSING,
                    toolName,
                    "Agent 工具执行缺少运行时上下文",
                    exception
            );
        }
    }

    private void validatePermission(AgentToolSchema schema, AgentRuntimeContext.Context context) {
        if (AgentToolPermissionType.LOGIN_USER.equals(schema.getPermissionType())
                && context.getUserId() == null) {
            throw new AgentToolException(
                    AgentToolErrorCode.TOOL_PERMISSION_DENIED,
                    schema.getToolName(),
                    "工具需要登录用户权限: " + schema.getToolName()
            );
        }
    }

    private void validateRequiredParams(AgentToolSchema schema, Map<String, Object> input) {
        if (CollectionUtils.isEmpty(schema.getInputParams())) {
            return;
        }

        for (AgentToolParamSchema param : schema.getInputParams()) {
            if (!Boolean.TRUE.equals(param.getRequired())) {
                continue;
            }

            Object value = input == null ? null : input.get(param.getName());
            if (isBlankValue(value)) {
                throw new AgentToolException(
                        AgentToolErrorCode.TOOL_PARAM_MISSING,
                        schema.getToolName(),
                        "工具缺少必填参数: " + param.getName()
                );
            }
        }
    }

    private void validateConfirmation(AgentToolSchema schema, AgentRuntimeContext.Context context) {
        if (!Boolean.TRUE.equals(schema.getRequiresUserConfirmation())) {
            return;
        }

        if (!context.isToolConfirmed(schema.getToolName())) {
            throw new AgentToolException(
                    AgentToolErrorCode.TOOL_CONFIRMATION_REQUIRED,
                    schema.getToolName(),
                    schema.getConfirmationMessage()
            );
        }
    }

    private boolean isBlankValue(Object value) {
        if (value == null) {
            return true;
        }
        if (value instanceof String text) {
            return text.isBlank();
        }
        return false;
    }
}
