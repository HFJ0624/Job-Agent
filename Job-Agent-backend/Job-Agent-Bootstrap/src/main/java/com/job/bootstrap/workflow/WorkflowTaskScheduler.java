package com.job.bootstrap.workflow;

import com.job.bootstrap.service.WorkflowTaskService;
import com.job.bootstrap.service.WorkflowTaskProgressService;
import com.job.common.entity.workflow.WorkflowTask;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 工作流任务调度器。
 */
@Slf4j
@Component
public class WorkflowTaskScheduler {

    private static final int POLL_LIMIT = 5;
    private static final int RUNNING_TIMEOUT_MINUTES = 30;
    private static final long POLL_FIXED_DELAY_MS = 60 * 60 * 1000L;

    private final WorkflowTaskService workflowTaskService;
    private final WorkflowTaskProgressService workflowTaskProgressService;
    private final Map<String, WorkflowTaskHandler> handlerMap;
    private final String workerId;

    public WorkflowTaskScheduler(
            WorkflowTaskService workflowTaskService,
            WorkflowTaskProgressService workflowTaskProgressService,
            List<WorkflowTaskHandler> handlers
    ) {
        this.workflowTaskService = workflowTaskService;
        this.workflowTaskProgressService = workflowTaskProgressService;
        this.handlerMap = handlers.stream().collect(Collectors.toMap(WorkflowTaskHandler::taskType, Function.identity()));
        this.workerId = buildWorkerId();
    }

    /**
     * 定时扫描并执行到期任务。
     *
     * 步骤:
     * 1. 先恢复超时 RUNNING 任务，解决服务重启或线程异常导致的卡死。
     * 2. 抢占到期任务，抢占成功后才在当前节点执行。
     * 3. handler 正常返回则标记成功，抛异常则交给任务服务计算重试状态。
     */
    @Scheduled(fixedDelay = POLL_FIXED_DELAY_MS)
    public void dispatchDueTasks() {
        try {
            workflowTaskService.recoverTimeoutRunningTasks(RUNNING_TIMEOUT_MINUTES);
            List<WorkflowTask> tasks = workflowTaskService.pollDueTasks(POLL_LIMIT, workerId);
            for (WorkflowTask task : tasks) {
                executeTask(task);
            }
        } catch (Exception exception) {
            log.warn("工作流任务调度失败，error={}", exception.getMessage(), exception);
        }
    }

    private void executeTask(WorkflowTask task) {
        long start = System.currentTimeMillis();
        WorkflowTaskHandler handler = handlerMap.get(task.getTaskType());
        if (handler == null) {
            workflowTaskProgressService.recordProgress(task.getId(), "查找处理器", task.getProgressPercent(), "未找到任务处理器", "ERROR", task.getTaskType());
            workflowTaskService.markFailure(task.getId(), new IllegalStateException("未找到任务处理器：" + task.getTaskType()));
            return;
        }

        try {
            workflowTaskProgressService.recordProgress(task.getId(), "开始执行", 5, "任务已被调度器抢占并开始执行", "INFO", null);
            String resultJson = handler.handle(task);
            workflowTaskService.markSuccess(task.getId(), resultJson, System.currentTimeMillis() - start);
            workflowTaskProgressService.recordProgress(task.getId(), "执行完成", 100, "任务执行成功", "INFO", null);
        } catch (Exception exception) {
            workflowTaskService.markFailure(task.getId(), exception);
            workflowTaskProgressService.recordProgress(task.getId(), "执行失败", task.getProgressPercent(), "任务执行失败，已进入重试/失败状态", "ERROR", exception.getMessage());
        }
    }

    private String buildWorkerId() {
        try {
            return InetAddress.getLocalHost().getHostName() + "-" + ProcessHandle.current().pid();
        } catch (Exception exception) {
            return "unknown-" + ProcessHandle.current().pid();
        }
    }
}
