package com.job.bootstrap.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.job.common.dto.workflow.WorkflowTaskCreateDTO;
import com.job.common.dto.workflow.WorkflowTaskQueryDTO;
import com.job.common.entity.workflow.WorkflowTask;
import com.job.common.vo.workflow.WorkflowTaskVO;

import java.util.List;

/**
 * 工作流任务队列服务。
 */
public interface WorkflowTaskService {

    WorkflowTaskVO createTask(WorkflowTaskCreateDTO request);

    IPage<WorkflowTaskVO> pageTasks(WorkflowTaskQueryDTO query);

    WorkflowTaskVO getDetail(Long id);

    List<WorkflowTask> pollDueTasks(int limit, String workerId);

    void markSuccess(Long taskId, String resultJson, long costTime);

    void markFailure(Long taskId, Exception exception);

    int recoverTimeoutRunningTasks(int timeoutMinutes);

    WorkflowTaskVO retryTask(Long id);

    WorkflowTaskVO cancelTask(Long id);
}
