package com.job.bootstrap.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.job.bootstrap.mapper.WorkflowTaskLogMapper;
import com.job.bootstrap.mapper.WorkflowTaskMapper;
import com.job.bootstrap.service.WorkflowTaskProgressService;
import com.job.common.entity.workflow.WorkflowTask;
import com.job.common.entity.workflow.WorkflowTaskLog;
import com.job.common.vo.workflow.WorkflowTaskLogVO;
import com.job.exception.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;

/**
 * 工作流任务进度服务实现。
 */
@Service
@RequiredArgsConstructor
public class WorkflowTaskProgressServiceImpl implements WorkflowTaskProgressService {

    private static final int NOT_DELETED = 0;

    private final WorkflowTaskMapper workflowTaskMapper;
    private final WorkflowTaskLogMapper workflowTaskLogMapper;

    /**
     * 记录任务阶段进度。
     *
     * 步骤:
     * 1. 先读取任务，确保日志一定能关联到真实任务。
     * 2. 更新任务主表的当前阶段和进度，列表页无需查日志表也能看到最新状态。
     * 3. 写入一条阶段日志，保留完整执行过程，方便 admin 排查失败原因。
     */
    @Override
    public void recordProgress(Long taskId, String stepName, Integer progressPercent, String message, String logLevel, String errorMsg) {
        WorkflowTask task = workflowTaskMapper.selectById(taskId);
        if (task == null || Integer.valueOf(1).equals(task.getIsDeleted())) {
            throw new BizException("工作流任务不存在");
        }

        Date now = new Date();
        task.setCurrentStep(stepName);
        task.setProgressPercent(normalizeProgress(progressPercent));
        task.setUpdateTime(now);
        workflowTaskMapper.updateById(task);

        WorkflowTaskLog log = new WorkflowTaskLog();
        log.setTaskId(task.getId());
        log.setTaskNo(task.getTaskNo());
        log.setTaskType(task.getTaskType());
        log.setStepName(stepName);
        log.setProgressPercent(task.getProgressPercent());
        log.setLogMessage(message);
        log.setLogLevel(logLevel == null ? "INFO" : logLevel);
        log.setErrorMsg(errorMsg);
        log.setIsDeleted(NOT_DELETED);
        log.setCreateTime(now);
        log.setUpdateTime(now);
        workflowTaskLogMapper.insert(log);
    }

    @Override
    public List<WorkflowTaskLogVO> listLogs(Long taskId) {
        return workflowTaskLogMapper.selectList(new LambdaQueryWrapper<WorkflowTaskLog>()
                        .eq(WorkflowTaskLog::getTaskId, taskId)
                        .eq(WorkflowTaskLog::getIsDeleted, NOT_DELETED)
                        .orderByAsc(WorkflowTaskLog::getCreateTime)
                        .orderByAsc(WorkflowTaskLog::getId))
                .stream()
                .map(WorkflowTaskLogVO::from)
                .toList();
    }

    private Integer normalizeProgress(Integer progressPercent) {
        if (progressPercent == null) {
            return null;
        }
        if (progressPercent < 0) {
            return 0;
        }
        return Math.min(progressPercent, 100);
    }
}
