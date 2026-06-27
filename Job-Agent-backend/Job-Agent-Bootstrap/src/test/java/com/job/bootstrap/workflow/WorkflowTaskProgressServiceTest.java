package com.job.bootstrap.workflow;

import com.job.bootstrap.mapper.WorkflowTaskLogMapper;
import com.job.bootstrap.mapper.WorkflowTaskMapper;
import com.job.bootstrap.service.impl.WorkflowTaskProgressServiceImpl;
import com.job.common.entity.workflow.WorkflowTask;
import com.job.common.entity.workflow.WorkflowTaskLog;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 工作流任务进度服务测试。
 */
class WorkflowTaskProgressServiceTest {

    @Test
    void shouldUpdateTaskProgressAndInsertStepLog() {
        WorkflowTaskMapper taskMapper = mock(WorkflowTaskMapper.class);
        WorkflowTaskLogMapper logMapper = mock(WorkflowTaskLogMapper.class);
        WorkflowTask task = new WorkflowTask();
        task.setId(7L);
        task.setProgressPercent(10);
        task.setCurrentStep("开始");

        when(taskMapper.selectById(7L)).thenReturn(task);

        WorkflowTaskProgressServiceImpl service = new WorkflowTaskProgressServiceImpl(taskMapper, logMapper);
        service.recordProgress(7L, "重建索引", 60, "正在写入 RAG 切片", "RUNNING", null);

        verify(taskMapper).updateById(any(WorkflowTask.class));
        verify(logMapper).insert(any(WorkflowTaskLog.class));
    }
}
