package com.job.bootstrap.workflow.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.job.bootstrap.rag.service.RagIndexService;
import com.job.bootstrap.workflow.WorkflowTaskHandler;
import com.job.common.entity.workflow.WorkflowTask;
import com.job.enums.WorkflowTaskType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * RAG 全量重建任务处理器。
 */
@Component
@RequiredArgsConstructor
public class RagRebuildAllWorkflowTaskHandler implements WorkflowTaskHandler {

    private final RagIndexService ragIndexService;
    private final ObjectMapper objectMapper;

    @Override
    public String taskType() {
        return WorkflowTaskType.RAG_REBUILD_ALL.name();
    }

    /**
     * 执行 RAG 全量重建。
     */
    @Override
    public String handle(WorkflowTask task) {
        try {
            return objectMapper.writeValueAsString(ragIndexService.rebuildAllUserKnowledge());
        } catch (Exception exception) {
            throw new IllegalStateException("RAG 全量重建失败：" + exception.getMessage(), exception);
        }
    }
}
