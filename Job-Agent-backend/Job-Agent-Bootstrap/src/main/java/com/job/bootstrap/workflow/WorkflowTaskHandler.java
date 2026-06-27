package com.job.bootstrap.workflow;

import com.job.common.entity.workflow.WorkflowTask;

/**
 * 工作流任务处理器。
 */
public interface WorkflowTaskHandler {

    /**
     * 当前处理器支持的任务类型。
     */
    String taskType();

    /**
     * 执行任务。
     *
     * @param task 已被调度器抢占的任务
     * @return 任务结果 JSON
     */
    String handle(WorkflowTask task);
}
