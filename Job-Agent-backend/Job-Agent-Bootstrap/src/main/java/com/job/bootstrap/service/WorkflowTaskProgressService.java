package com.job.bootstrap.service;

import com.job.common.vo.workflow.WorkflowTaskLogVO;

import java.util.List;

/**
 * 工作流任务进度与阶段日志服务。
 */
public interface WorkflowTaskProgressService {

    void recordProgress(Long taskId, String stepName, Integer progressPercent, String message, String logLevel, String errorMsg);

    List<WorkflowTaskLogVO> listLogs(Long taskId);
}
