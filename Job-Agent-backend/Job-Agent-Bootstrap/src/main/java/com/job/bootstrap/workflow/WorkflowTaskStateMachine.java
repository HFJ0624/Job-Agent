package com.job.bootstrap.workflow;

import com.job.enums.WorkflowTaskStatus;
import org.springframework.stereotype.Component;

/**
 * 工作流任务状态机。
 */
@Component
public class WorkflowTaskStateMachine {

    /**
     * 根据失败后的重试次数计算下一个状态。
     *
     * @param retryCount 失败后已经累计的重试次数
     * @param maxRetryCount 最大允许重试次数
     * @return 下一个任务状态
     */
    public WorkflowTaskStatus nextStatusAfterFailure(int retryCount, int maxRetryCount) {
        return retryCount < maxRetryCount
                ? WorkflowTaskStatus.FAILED_RETRYABLE
                : WorkflowTaskStatus.FAILED_FINAL;
    }

    /**
     * RUNNING 超时任务恢复时的状态计算。
     */
    public WorkflowTaskStatus nextStatusAfterTimeout(int retryCount, int maxRetryCount) {
        return nextStatusAfterFailure(retryCount, maxRetryCount);
    }
}
