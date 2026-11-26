package com.job.bootstrap.workflow.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.job.bootstrap.service.AgentEvalService;
import com.job.bootstrap.service.WorkflowTaskProgressService;
import com.job.bootstrap.workflow.WorkflowTaskHandler;
import com.job.common.entity.workflow.WorkflowTask;
import com.job.enums.WorkflowTaskType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 功能: Agent Eval 批次异步执行处理器（任务10）。
 *
 * 设计说明:
 * 1. bizId = evalRunId；requestJson 里可带 caseId（仅单条用例批次需要）。
 * 2. 真正执行委托 AgentEvalService.executeRun（幂等：批次已结束不重复跑）。
 * 3. 复用 WorkflowTaskScheduler 的抢占、重试、进度与观测，执行失败会按任务重试策略重投。
 */
@Component
@RequiredArgsConstructor
public class AgentEvalRunWorkflowTaskHandler implements WorkflowTaskHandler {

    private final AgentEvalService agentEvalService;
    private final WorkflowTaskProgressService workflowTaskProgressService;
    private final ObjectMapper objectMapper;

    @Override
    public String taskType() {
        return WorkflowTaskType.AGENT_EVAL_RUN.name();
    }

    @Override
    public String handle(WorkflowTask task) {
        if (task.getBizId() == null) {
            throw new IllegalArgumentException("Eval 批次任务缺少 bizId（runId）");
        }
        Long caseId = resolveCaseId(task.getRequestJson());
        workflowTaskProgressService.recordProgress(
                task.getId(), "Eval 批次执行", 20, "开始执行批次 " + task.getBizId(), "INFO", null);
        try {
            return objectMapper.writeValueAsString(agentEvalService.executeRun(task.getBizId(), caseId));
        } catch (Exception exception) {
            throw new IllegalStateException("Eval 批次执行失败：" + exception.getMessage(), exception);
        }
    }

    // 从 requestJson 解析 caseId（仅单条用例批次有；缺失或非法都按 null 处理）
    private Long resolveCaseId(String requestJson) {
        if (requestJson == null || requestJson.isBlank()) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(requestJson);
            if (node.hasNonNull("caseId")) {
                return node.get("caseId").asLong();
            }
        } catch (Exception ignored) {
            // requestJson 非法时按无 caseId 处理
        }
        return null;
    }
}
