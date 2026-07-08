package com.job.bootstrap.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.job.common.dto.workflow.WorkflowTaskCreateDTO;
import com.job.common.dto.workflow.WorkflowTaskQueryDTO;
import com.job.common.entity.workflow.WorkflowTask;
import com.job.common.vo.workflow.WorkflowTaskVO;

import java.util.List;

/**
 * 工作流任务队列服务。
 *
 * <p>核心职责：为系统异步工作流提供任务队列管理能力，支持任务创建、查询、调度拉取、成功/失败标记、超时恢复、重试与取消等全生命周期操作。</p>
 *
 * <p>所属业务模块：工作流引擎 / 任务调度</p>
 *
 * <p>主要调用链：各业务 Controller / 定时调度器 → WorkflowTaskService → 工作流任务 Mapper / 消息队列 / Worker 执行器</p>
 */
public interface WorkflowTaskService {

    /**
     * 创建新的工作流任务。
     *
     * @param request 任务创建参数（包含任务类型、业务标识、执行参数、计划执行时间等）
     * @return 创建后的任务详情
     */
    WorkflowTaskVO createTask(WorkflowTaskCreateDTO request);

    /**
     * 分页查询工作流任务列表。
     *
     * @param query 查询条件（包含任务类型、状态、业务标识、时间范围等过滤条件）
     * @return 工作流任务分页结果
     */
    IPage<WorkflowTaskVO> pageTasks(WorkflowTaskQueryDTO query);

    /**
     * 查询指定工作流任务详情。
     *
     * @param id 任务 ID
     * @return 任务完整详情，包含执行参数、当前状态、结果、日志关联等
     */
    WorkflowTaskVO getDetail(Long id);

    /**
     * 拉取已到执行时间且待处理的工作流任务列表。
     *
     * @param limit    拉取数量上限
     * @param workerId 当前 Worker 标识，用于任务锁定与负载均衡
     * @return 待执行的工作流任务列表
     */
    List<WorkflowTask> pollDueTasks(int limit, String workerId);

    /**
     * 标记指定工作流任务执行成功。
     *
     * @param taskId     任务 ID
     * @param resultJson 任务执行结果 JSON 字符串
     * @param costTime   任务实际执行耗时（毫秒）
     */
    void markSuccess(Long taskId, String resultJson, long costTime);

    /**
     * 标记指定工作流任务执行失败。
     *
     * @param taskId    任务 ID
     * @param exception 任务执行抛出的异常信息
     */
    void markFailure(Long taskId, Exception exception);

    /**
     * 恢复因 Worker 宕机等原因导致超时未完成的运行中任务。
     *
     * @param timeoutMinutes 任务执行超时阈值（分钟），超过该时间仍处运行中状态则触发恢复
     * @return 本次成功恢复并重置为待处理状态的任务数量
     */
    int recoverTimeoutRunningTasks(int timeoutMinutes);

    /**
     * 重试指定失败或取消的工作流任务。
     *
     * @param id 任务 ID
     * @return 重试后的任务详情
     */
    WorkflowTaskVO retryTask(Long id);

    /**
     * 取消指定待执行或运行中的工作流任务。
     *
     * @param id 任务 ID
     * @return 取消后的任务详情
     */
    WorkflowTaskVO cancelTask(Long id);
}
