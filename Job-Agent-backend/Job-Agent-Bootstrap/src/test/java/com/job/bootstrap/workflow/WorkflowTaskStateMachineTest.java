package com.job.bootstrap.workflow;

import com.job.enums.WorkflowTaskStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 工作流任务状态机测试。
 */
class WorkflowTaskStateMachineTest {

    @Test
    void shouldRetryWhenRetryCountIsBelowMaxRetryCount() {
        WorkflowTaskStateMachine stateMachine = new WorkflowTaskStateMachine();

        assertThat(stateMachine.nextStatusAfterFailure(1, 3)).isEqualTo(WorkflowTaskStatus.FAILED_RETRYABLE);
    }

    @Test
    void shouldStopRetryWhenRetryCountReachesMaxRetryCount() {
        WorkflowTaskStateMachine stateMachine = new WorkflowTaskStateMachine();

        assertThat(stateMachine.nextStatusAfterFailure(3, 3)).isEqualTo(WorkflowTaskStatus.FAILED_FINAL);
    }

    @Test
    void shouldRecoverTimeoutRunningTaskToRetryableStatus() {
        WorkflowTaskStateMachine stateMachine = new WorkflowTaskStateMachine();

        assertThat(stateMachine.nextStatusAfterTimeout(0, 3)).isEqualTo(WorkflowTaskStatus.FAILED_RETRYABLE);
        assertThat(stateMachine.nextStatusAfterTimeout(3, 3)).isEqualTo(WorkflowTaskStatus.FAILED_FINAL);
    }
}
