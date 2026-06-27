package com.job.bootstrap.workflow.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.job.bootstrap.service.AgentEvalService;
import com.job.bootstrap.workflow.WorkflowTaskHandler;
import com.job.common.entity.workflow.WorkflowTask;
import com.job.enums.WorkflowTaskType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Agent Eval 数据集批量回归任务处理器。
 */
@Component
@RequiredArgsConstructor
public class AgentEvalRunDatasetWorkflowTaskHandler implements WorkflowTaskHandler {

    private final AgentEvalService agentEvalService;
    private final ObjectMapper objectMapper;

    @Override
    public String taskType() {
        return WorkflowTaskType.AGENT_EVAL_RUN_DATASET.name();
    }

    /**
     * 执行指定数据集 Eval。
     */
    @Override
    public String handle(WorkflowTask task) {
        if (task.getBizId() == null) {
            throw new IllegalArgumentException("Eval 数据集任务缺少 bizId");
        }
        try {
            return objectMapper.writeValueAsString(agentEvalService.runDataset(task.getBizId()));
        } catch (Exception exception) {
            throw new IllegalStateException("Eval 数据集执行失败：" + exception.getMessage(), exception);
        }
    }
}
