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
 *
 * <p>核心职责：记录工作流任务的阶段进度和日志，支持任务执行过程的可观测性和故障排查。</p>
 *
 * <p>所属业务模块：工作流调度模块（workflow）</p>
 *
 * <p>主要调用链：
 * <ol>
 *   <li>任务执行器在每个阶段完成后调用 {@link #recordProgress} 记录进度；</li>
 *   <li>进度同时更新任务主表和写入日志表，列表页无需查日志即可看到最新状态；</li>
 *   <li>管理员或前端调用 {@link #listLogs} 查看任务完整执行日志。</li>
 * </ol>
 * </p>
 *
 * <p>与其他核心组件的关系：
 * <ul>
 *   <li>依赖 {@link WorkflowTaskMapper} 更新任务主表进度；</li>
 *   <li>依赖 {@link WorkflowTaskLogMapper} 写入阶段日志。</li>
 * </ul>
 * </p>
 *
 * <p>设计说明：
 * <ol>
 *   <li>进度记录采用双写模式，任务表存最新状态，日志表存完整轨迹；</li>
 *   <li>进度百分比限制在 0-100 范围内，非法值自动归边；</li>
 *   <li>日志按创建时间正序排列，便于按时间线展示执行过程。</li>
 * </ol>
 * </p>
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

    /**
     * 查询任务日志列表，按创建时间正序排列。
     *
     * @param taskId 任务 ID
     * @return 日志 VO 列表
     */
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

    /**
     * 规范化进度百分比，限制在 0-100 范围内，null 保留原样。
     *
     * @param progressPercent 原始进度
     * @return 规范化后的进度
     */
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
