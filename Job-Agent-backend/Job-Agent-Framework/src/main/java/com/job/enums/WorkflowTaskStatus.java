package com.job.enums;

/**
 * 工作流任务状态。
 */
public enum WorkflowTaskStatus {

    /**
     * 等待调度器执行。
     */
    PENDING,

    /**
     * 已被某个调度器实例抢占，正在执行。
     */
    RUNNING,

    /**
     * 执行成功。
     */
    SUCCESS,

    /**
     * 执行失败，但还没有达到最大重试次数。
     */
    FAILED_RETRYABLE,

    /**
     * 执行失败，并且已经达到最大重试次数。
     */
    FAILED_FINAL,

    /**
     * 管理员手动取消。
     */
    CANCELLED
}
