package com.job.bootstrap.agent.application.chat.support;

import com.job.common.agent.tool.AgentToolSchema;
import com.job.common.vo.agent.AgentPlanStepVO;
import com.job.common.vo.agent.AgentPlanVO;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 功能: 聊天 Trace 载荷组装（从 AgentChatServiceImpl 抽出）。
 *
 * <p>只负责把计划/工具/答复转成可落库的 Map，不含业务判断；
 * 字段名与结构保持不变，历史 Trace 数据可继续读取。</p>
 */
public final class ChatTraceAssembler {

    private ChatTraceAssembler() {
    }

    public static Map<String, Object> buildPlanOnlyTraceInput(String message, AgentPlanVO plan) {
        return ChatTraceAssembler.buildPlanOnlyTraceInput(message, plan, List.of());
    }

    public static Map<String, Object> buildPlanOnlyTraceInput(
            String message,
            AgentPlanVO plan,
            List<String> confirmedToolNames
    ) {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("message", message);
        input.put("confirmedToolNames", confirmedToolNames == null ? List.of() : confirmedToolNames);
        input.put("agentPlan", ChatTraceAssembler.buildPlanSnapshot(plan));
        return input;
    }

    public static Map<String, Object> buildPlanOnlyTraceOutput(String answer, AgentPlanVO plan) {
        return ChatTraceAssembler.buildPlanOnlyTraceOutput(answer, plan, List.of());
    }

    public static Map<String, Object> buildPlanOnlyTraceOutput(
            String answer,
            AgentPlanVO plan,
            List<AgentToolSchema> unconfirmedTools
    ) {
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("answer", answer);
        output.put("agentPlan", ChatTraceAssembler.buildPlanSnapshot(plan));
        output.put("unconfirmedTools", ChatTraceAssembler.buildToolSchemaSnapshots(unconfirmedTools));
        return output;
    }

    public static List<Map<String, Object>> buildToolSchemaSnapshots(List<AgentToolSchema> schemas) {
        if (CollectionUtils.isEmpty(schemas)) {
            return List.of();
        }

        List<Map<String, Object>> snapshots = new ArrayList<>();
        for (AgentToolSchema schema : schemas) {
            Map<String, Object> snapshot = new LinkedHashMap<>();
            snapshot.put("toolName", schema.getToolName());
            snapshot.put("displayName", schema.getDisplayName());
            snapshot.put("permissionType", schema.getPermissionType());
            snapshot.put("sideEffectType", schema.getSideEffectType());
            snapshot.put("hasSideEffect", schema.getHasSideEffect());
            snapshot.put("requiresUserConfirmation", schema.getRequiresUserConfirmation());
            snapshot.put("confirmationMessage", schema.getConfirmationMessage());
            snapshots.add(snapshot);
        }
        return snapshots;
    }

    public static Map<String, Object> buildPlanSnapshot(AgentPlanVO plan) {
        if (plan == null) {
            return Map.of();
        }

        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("id", plan.getId());
        snapshot.put("traceId", plan.getTraceId());
        snapshot.put("userId", plan.getUserId());
        snapshot.put("conversationId", plan.getConversationId());
        snapshot.put("intentCode", plan.getIntentCode());
        snapshot.put("planTitle", plan.getPlanTitle());
        snapshot.put("planSummary", plan.getPlanSummary());
        snapshot.put("requiredParamsJson", plan.getRequiredParamsJson());
        snapshot.put("extractedParamsJson", plan.getExtractedParamsJson());
        snapshot.put("missingParamsJson", plan.getMissingParamsJson());
        snapshot.put("status", plan.getStatus());

        List<Map<String, Object>> steps = new ArrayList<>();
        if (!CollectionUtils.isEmpty(plan.getSteps())) {
            for (AgentPlanStepVO step : plan.getSteps()) {
                Map<String, Object> stepSnapshot = new LinkedHashMap<>();
                stepSnapshot.put("id", step.getId());
                stepSnapshot.put("stepNo", step.getStepNo());
                stepSnapshot.put("stepName", step.getStepName());
                stepSnapshot.put("stepGoal", step.getStepGoal());
                stepSnapshot.put("toolName", step.getToolName());
                stepSnapshot.put("toolInputSchema", step.getToolInputSchema());
                stepSnapshot.put("completionCriteria", step.getCompletionCriteria());
                stepSnapshot.put("status", step.getStatus());
                steps.add(stepSnapshot);
            }
        }
        snapshot.put("steps", steps);
        return snapshot;
    }
}
