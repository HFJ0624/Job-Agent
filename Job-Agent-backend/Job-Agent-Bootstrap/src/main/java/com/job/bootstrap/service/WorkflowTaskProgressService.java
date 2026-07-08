package com.job.bootstrap.service;

import com.job.common.vo.workflow.WorkflowTaskLogVO;

import java.util.List;

/**
 * 工作流任务进度与阶段日志服务。
 *
 * <p>核心职责：为异步工作流任务提供进度追踪与阶段日志记录能力，支持实时记录任务执行步骤、进度百分比、日志消息及异常信息，并提供历史日志查询。</p>
 *
 * <p>所属业务模块：工作流引擎 / 任务调度</p>
 *
 * <p>主要调用链：工作流执行器 / Worker → WorkflowTaskProgressService → 任务日志 Mapper / 任务状态存储</p>
 */
public interface WorkflowTaskProgressService {

    /**
     * 记录工作流任务的执行进度与阶段日志。
     *
     * @param taskId          工作流任务 ID
     * @param stepName        当前执行步骤名称
     * @param progressPercent 当前整体进度百分比（0-100）
     * @param message         日志消息内容
     * @param logLevel        日志级别（如 INFO、WARN、ERROR）
     * @param errorMsg        异常信息，无异常时传空
     */
    void recordProgress(Long taskId, String stepName, Integer progressPercent, String message, String logLevel, String errorMsg);

    /**
     * 查询指定工作流任务的历史执行日志列表。
     *
     * @param taskId 工作流任务 ID
     * @return 按时间顺序排列的任务执行日志列表
     */
    List<WorkflowTaskLogVO> listLogs(Long taskId);
}
