package com.job.bootstrap.workflow.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.job.bootstrap.rag.service.RagIndexService;
import com.job.bootstrap.workflow.WorkflowTaskHandler;
import com.job.common.entity.workflow.WorkflowTask;
import com.job.enums.WorkflowTaskType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 指定用户 RAG 重建任务处理器。
 */
@Component
@RequiredArgsConstructor
public class RagRebuildUserWorkflowTaskHandler implements WorkflowTaskHandler {

    private final RagIndexService ragIndexService;
    private final ObjectMapper objectMapper;

    @Override
    public String taskType() {
        return WorkflowTaskType.RAG_REBUILD_USER.name();
    }

    /**
     * 执行指定用户 RAG 重建。
     */
    @Override
    public String handle(WorkflowTask task) {
        if (task.getUserId() == null) {
            throw new IllegalArgumentException("RAG 用户重建任务缺少 userId");
        }
        try {
            return objectMapper.writeValueAsString(ragIndexService.rebuildUserKnowledge(task.getUserId()));
        } catch (Exception exception) {
            throw new IllegalStateException("RAG 用户重建失败：" + exception.getMessage(), exception);
        }
    }
}
