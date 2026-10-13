package com.job.bootstrap.workflow;

import com.job.bootstrap.service.WorkflowTaskProgressService;
import com.job.bootstrap.service.WorkflowTaskService;
import com.job.common.entity.workflow.WorkflowTask;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * WorkflowTaskScheduler 单元测试。
 *
 * 覆盖重点:
 * 1. 正常路径：恢复超时任务 -> 抢占到期任务 -> 执行 handler -> 标记成功。
 * 2. 失败路径：handler 抛异常 -> 交给任务服务计算重试状态（markFailure）。
 * 3. 无处理器：taskType 无对应 handler -> markFailure(IllegalStateException)。
 */
class WorkflowTaskSchedulerTest {

    private final WorkflowTaskService workflowTaskService = mock(WorkflowTaskService.class);
    private final WorkflowTaskProgressService workflowTaskProgressService = mock(WorkflowTaskProgressService.class);
    private final WorkflowTaskHandler handler = mock(WorkflowTaskHandler.class);

    private WorkflowTaskScheduler scheduler;

    @BeforeEach
    void setUp() {
        when(handler.taskType()).thenReturn("INTERVIEW_EMAIL_NOTIFY");
        scheduler = new WorkflowTaskScheduler(workflowTaskService, workflowTaskProgressService, List.of(handler));
    }

    @Test
    void shouldExecuteClaimedTaskAndMarkSuccess() {
        WorkflowTask task = buildTask("INTERVIEW_EMAIL_NOTIFY");
        when(workflowTaskService.pollDueTasks(anyInt(), anyString())).thenReturn(List.of(task));
        when(handler.handle(task)).thenReturn("{\"sent\":true}");

        scheduler.dispatchDueTasks();

        verify(workflowTaskService).recoverTimeoutRunningTasks(anyInt());
        verify(workflowTaskService).pollDueTasks(anyInt(), anyString());
        verify(handler).handle(task);
        verify(workflowTaskService).markSuccess(eq(1L), anyString(), anyLong());
    }

    @Test
    void handlerExceptionShouldMarkFailure() {
        WorkflowTask task = buildTask("INTERVIEW_EMAIL_NOTIFY");
        when(workflowTaskService.pollDueTasks(anyInt(), anyString())).thenReturn(List.of(task));
        when(handler.handle(task)).thenThrow(new RuntimeException("boom"));

        scheduler.dispatchDueTasks();

        verify(workflowTaskService).markFailure(eq(1L), any(RuntimeException.class));
    }

    @Test
    void missingHandlerShouldMarkFailure() {
        WorkflowTask task = buildTask("RAG_REBUILD_ALL");
        when(workflowTaskService.pollDueTasks(anyInt(), anyString())).thenReturn(List.of(task));

        scheduler.dispatchDueTasks();

        verify(workflowTaskService).markFailure(eq(1L), any(IllegalStateException.class));
    }

    private WorkflowTask buildTask(String taskType) {
        WorkflowTask task = new WorkflowTask();
        task.setId(1L);
        task.setTaskType(taskType);
        task.setProgressPercent(0);
        return task;
    }
}
