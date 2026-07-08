package com.job.bootstrap.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.job.bootstrap.mapper.WorkflowTaskMapper;
import com.job.bootstrap.service.WorkflowTaskService;
import com.job.bootstrap.workflow.WorkflowTaskStateMachine;
import com.job.common.dto.workflow.WorkflowTaskCreateDTO;
import com.job.common.dto.workflow.WorkflowTaskQueryDTO;
import com.job.common.entity.workflow.WorkflowTask;
import com.job.common.vo.workflow.WorkflowTaskVO;
import com.job.enums.WorkflowTaskStatus;
import com.job.enums.WorkflowTaskType;
import com.job.exception.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * 工作流任务队列服务实现。
 *
 * <p>核心职责：提供异步工作流任务的创建、查询、调度抢占、成功/失败标记、超时恢复、手动重试和取消等全生命周期管理。</p>
 *
 * <p>所属业务模块：工作流调度模块（workflow）</p>
 *
 * <p>主要调用链：
 * <ol>
 *   <li>业务模块调用 {@link #createTask} 创建异步任务；</li>
 *   <li>调度器定期调用 {@link #pollDueTasks} 捞取并抢占到期任务；</li>
 *   <li>任务执行器完成后调用 {@link #markSuccess} 或 {@link #markFailure} 更新状态；</li>
 *   <li>调度器调用 {@link #recoverTimeoutRunningTasks} 恢复长时间未完成的任务；</li>
 *   <li>管理员可通过 {@link #retryTask} 和 {@link #cancelTask} 手动干预任务。</li>
 * </ol>
 * </p>
 *
 * <p>与其他核心组件的关系：
 * <ul>
 *   <li>依赖 {@link WorkflowTaskMapper} 进行任务数据持久化；</li>
 *   <li>依赖 {@link WorkflowTaskStateMachine} 计算失败后的下一状态（重试或最终失败）；</li>
 *   <li>调度器和执行器通过本服务与任务队列交互。</li>
 * </ul>
 * </p>
 *
 * <p>设计说明：
 * <ol>
 *   <li>任务创建时状态为 PENDING，nextRunTime 设为当前时间，让调度器尽快捞取；</li>
 *   <li>pollDueTasks 使用乐观锁（id + 原状态条件更新）实现分布式抢占，避免多调度器重复执行；</li>
 *   <li>失败自动重试，超过最大重试次数后标记为最终失败；</li>
 *   <li>超时恢复机制保证 RUNNING 任务不会因执行器宕机而永久挂起。</li>
 * </ol>
 * </p>
 */
@Service
@RequiredArgsConstructor
public class WorkflowTaskServiceImpl implements WorkflowTaskService {

    private static final int NOT_DELETED = 0;
    private static final int DELETED = 1;
    private static final long DEFAULT_PAGE_NUM = 1L;
    private static final long DEFAULT_PAGE_SIZE = 10L;
    private static final long MAX_PAGE_SIZE = 100L;
    private static final int DEFAULT_MAX_RETRY_COUNT = 3;
    private static final int DEFAULT_RETRY_INTERVAL_SECONDS = 60;

    private final WorkflowTaskMapper workflowTaskMapper;
    private final WorkflowTaskStateMachine stateMachine;

    /**
     * 创建异步工作流任务。
     *
     * 步骤:
     * 1. 校验任务类型必须是系统白名单，避免前端传任意字符串触发未知逻辑。
     * 2. 初始化状态为 PENDING，并设置 nextRunTime=当前时间，让调度器尽快捞取。
     * 3. 保存 requestJson/bizId/userId，后续 handler 只依赖任务快照执行。
     */
    @Override
    public WorkflowTaskVO createTask(WorkflowTaskCreateDTO request) {
        WorkflowTaskType taskType = parseTaskType(request.getTaskType());
        Date now = new Date();

        WorkflowTask task = new WorkflowTask();
        task.setTaskNo(buildTaskNo(taskType));
        task.setTaskType(taskType.name());
        task.setBizId(request.getBizId());
        task.setUserId(request.getUserId());
        task.setRequestJson(request.getRequestJson());
        task.setStatus(WorkflowTaskStatus.PENDING.name());
        task.setProgressPercent(0);
        task.setCurrentStep("等待执行");
        task.setRetryCount(0);
        task.setMaxRetryCount(safePositive(request.getMaxRetryCount(), DEFAULT_MAX_RETRY_COUNT));
        task.setRetryIntervalSeconds(safePositive(request.getRetryIntervalSeconds(), DEFAULT_RETRY_INTERVAL_SECONDS));
        task.setNextRunTime(now);
        task.setIsDeleted(NOT_DELETED);
        task.setCreateTime(now);
        task.setUpdateTime(now);
        workflowTaskMapper.insert(task);
        return WorkflowTaskVO.from(task);
    }

    /**
     * 分页查询工作流任务列表。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    @Override
    public IPage<WorkflowTaskVO> pageTasks(WorkflowTaskQueryDTO query) {
        WorkflowTaskQueryDTO safeQuery = query == null ? new WorkflowTaskQueryDTO() : query;
        Page<WorkflowTask> page = new Page<>(safePageNum(safeQuery.getPageNum()), safePageSize(safeQuery.getPageSize()));
        LambdaQueryWrapper<WorkflowTask> wrapper = new LambdaQueryWrapper<WorkflowTask>()
                .eq(WorkflowTask::getIsDeleted, NOT_DELETED);
        if (StringUtils.hasText(safeQuery.getTaskNo())) {
            wrapper.like(WorkflowTask::getTaskNo, safeQuery.getTaskNo().trim());
        }
        if (StringUtils.hasText(safeQuery.getTaskType())) {
            wrapper.eq(WorkflowTask::getTaskType, safeQuery.getTaskType().trim());
        }
        if (StringUtils.hasText(safeQuery.getStatus())) {
            wrapper.eq(WorkflowTask::getStatus, safeQuery.getStatus().trim());
        }
        if (safeQuery.getBizId() != null) {
            wrapper.eq(WorkflowTask::getBizId, safeQuery.getBizId());
        }
        if (safeQuery.getUserId() != null) {
            wrapper.eq(WorkflowTask::getUserId, safeQuery.getUserId());
        }
        if (StringUtils.hasText(safeQuery.getStartTime())) {
            wrapper.ge(WorkflowTask::getCreateTime, safeQuery.getStartTime().trim());
        }
        if (StringUtils.hasText(safeQuery.getEndTime())) {
            wrapper.le(WorkflowTask::getCreateTime, safeQuery.getEndTime().trim());
        }
        wrapper.orderByDesc(WorkflowTask::getCreateTime);
        return workflowTaskMapper.selectPage(page, wrapper).convert(WorkflowTaskVO::from);
    }

    /**
     * 查询工作流任务详情。
     *
     * @param id 任务 ID
     * @return 任务详情 VO
     */
    @Override
    public WorkflowTaskVO getDetail(Long id) {
        return WorkflowTaskVO.from(loadTask(id));
    }

    /**
     * 捞取并抢占到期任务。
     *
     * 步骤:
     * 1. 查询 PENDING 或到期 FAILED_RETRYABLE，且 nextRunTime <= now 的任务。
     * 2. 对每条任务使用 id + 原状态做条件更新，只有更新成功的线程才算抢占成功。
     * 3. 抢占成功后重新查询最新任务快照返回给调度器执行。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<WorkflowTask> pollDueTasks(int limit, String workerId) {
        Date now = new Date();
        List<WorkflowTask> candidates = workflowTaskMapper.selectList(new LambdaQueryWrapper<WorkflowTask>()
                .eq(WorkflowTask::getIsDeleted, NOT_DELETED)
                .le(WorkflowTask::getNextRunTime, now)
                .in(WorkflowTask::getStatus, WorkflowTaskStatus.PENDING.name(), WorkflowTaskStatus.FAILED_RETRYABLE.name())
                .orderByAsc(WorkflowTask::getNextRunTime)
                .last("LIMIT " + Math.max(1, limit)));

        return candidates.stream()
                .filter(task -> tryLockTask(task, workerId, now))
                .map(task -> workflowTaskMapper.selectById(task.getId()))
                .toList();
    }

    /**
     * 标记任务执行成功。
     *
     * @param taskId     任务 ID
     * @param resultJson 执行结果 JSON
     * @param costTime   执行耗时（毫秒）
     */
    @Override
    public void markSuccess(Long taskId, String resultJson, long costTime) {
        Date now = new Date();
        WorkflowTask task = loadTask(taskId);
        task.setStatus(WorkflowTaskStatus.SUCCESS.name());
        task.setProgressPercent(100);
        task.setCurrentStep("执行完成");
        task.setResultJson(resultJson);
        task.setErrorMsg(null);
        task.setFinishTime(now);
        task.setCostTime(costTime);
        task.setLockedBy(null);
        task.setLockTime(null);
        task.setUpdateTime(now);
        workflowTaskMapper.updateById(task);
    }

    /**
     * 记录任务失败并计算是否需要重试。
     */
    @Override
    public void markFailure(Long taskId, Exception exception) {
        Date now = new Date();
        WorkflowTask task = loadTask(taskId);
        int retryCount = safeInt(task.getRetryCount()) + 1;
        int maxRetryCount = safePositive(task.getMaxRetryCount(), DEFAULT_MAX_RETRY_COUNT);
        WorkflowTaskStatus nextStatus = stateMachine.nextStatusAfterFailure(retryCount, maxRetryCount);

        task.setRetryCount(retryCount);
        task.setStatus(nextStatus.name());
        task.setCurrentStep(WorkflowTaskStatus.FAILED_RETRYABLE.equals(nextStatus) ? "等待重试" : "最终失败");
        task.setErrorMsg(shortError(exception));
        task.setFinishTime(now);
        task.setLockedBy(null);
        task.setLockTime(null);
        task.setUpdateTime(now);
        if (WorkflowTaskStatus.FAILED_RETRYABLE.equals(nextStatus)) {
            task.setNextRunTime(afterSeconds(safePositive(task.getRetryIntervalSeconds(), DEFAULT_RETRY_INTERVAL_SECONDS)));
        }
        workflowTaskMapper.updateById(task);
    }

    /**
     * 恢复长时间卡在 RUNNING 的任务。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int recoverTimeoutRunningTasks(int timeoutMinutes) {
        Date cutoff = Date.from(LocalDateTime.now()
                .minusMinutes(Math.max(1, timeoutMinutes))
                .atZone(ZoneId.systemDefault())
                .toInstant());
        List<WorkflowTask> timeoutTasks = workflowTaskMapper.selectList(new LambdaQueryWrapper<WorkflowTask>()
                .eq(WorkflowTask::getIsDeleted, NOT_DELETED)
                .eq(WorkflowTask::getStatus, WorkflowTaskStatus.RUNNING.name())
                .lt(WorkflowTask::getLockTime, cutoff));

        Date now = new Date();
        for (WorkflowTask task : timeoutTasks) {
            int retryCount = safeInt(task.getRetryCount()) + 1;
            WorkflowTaskStatus nextStatus = stateMachine.nextStatusAfterTimeout(
                    retryCount,
                    safePositive(task.getMaxRetryCount(), DEFAULT_MAX_RETRY_COUNT)
            );
            task.setRetryCount(retryCount);
            task.setStatus(nextStatus.name());
            task.setCurrentStep(WorkflowTaskStatus.FAILED_RETRYABLE.equals(nextStatus) ? "超时恢复，等待重试" : "超时恢复，最终失败");
            task.setErrorMsg("任务执行超时，已由调度器恢复");
            task.setLockedBy(null);
            task.setLockTime(null);
            task.setUpdateTime(now);
            if (WorkflowTaskStatus.FAILED_RETRYABLE.equals(nextStatus)) {
                task.setNextRunTime(now);
            }
            workflowTaskMapper.updateById(task);
        }
        return timeoutTasks.size();
    }

    /**
     * 手动重试任务。
     *
     * @param id 任务 ID
     * @return 重试后的任务 VO
     * @throws BizException 任务状态不允许重试时抛出
     */
    @Override
    public WorkflowTaskVO retryTask(Long id) {
        WorkflowTask task = loadTask(id);
        if (WorkflowTaskStatus.SUCCESS.name().equals(task.getStatus())
                || WorkflowTaskStatus.RUNNING.name().equals(task.getStatus())) {
            throw new BizException("当前任务状态不允许手动重试");
        }
        Date now = new Date();
        task.setStatus(WorkflowTaskStatus.PENDING.name());
        task.setProgressPercent(0);
        task.setCurrentStep("手动重试，等待执行");
        task.setNextRunTime(now);
        task.setLockedBy(null);
        task.setLockTime(null);
        task.setErrorMsg(null);
        task.setUpdateTime(now);
        workflowTaskMapper.updateById(task);
        return WorkflowTaskVO.from(task);
    }

    /**
     * 取消工作流任务。
     *
     * @param id 任务 ID
     * @return 取消后的任务 VO
     * @throws BizException 已成功任务不能取消
     */
    @Override
    public WorkflowTaskVO cancelTask(Long id) {
        WorkflowTask task = loadTask(id);
        if (WorkflowTaskStatus.SUCCESS.name().equals(task.getStatus())) {
            throw new BizException("已成功任务不能取消");
        }
        Date now = new Date();
        task.setStatus(WorkflowTaskStatus.CANCELLED.name());
        task.setCurrentStep("已取消");
        task.setLockedBy(null);
        task.setLockTime(null);
        task.setUpdateTime(now);
        workflowTaskMapper.updateById(task);
        return WorkflowTaskVO.from(task);
    }

    /**
     * 尝试抢占任务，使用乐观锁保证同一任务只被一个执行器获取。
     *
     * @param task     待抢占任务
     * @param workerId 执行器标识
     * @param now      当前时间
     * @return 抢占成功返回 true，否则返回 false
     */
    private boolean tryLockTask(WorkflowTask task, String workerId, Date now) {
        int updated = workflowTaskMapper.update(null, new LambdaUpdateWrapper<WorkflowTask>()
                .set(WorkflowTask::getStatus, WorkflowTaskStatus.RUNNING.name())
                .set(WorkflowTask::getCurrentStep, "任务启动")
                .set(WorkflowTask::getLockedBy, workerId)
                .set(WorkflowTask::getLockTime, now)
                .set(WorkflowTask::getStartTime, now)
                .set(WorkflowTask::getUpdateTime, now)
                .eq(WorkflowTask::getId, task.getId())
                .eq(WorkflowTask::getStatus, task.getStatus())
                .eq(WorkflowTask::getIsDeleted, NOT_DELETED));
        return updated > 0;
    }

    /**
     * 加载并校验任务实体。
     *
     * @param id 任务 ID
     * @return 非空且未删除的任务实体
     * @throws BizException 任务不存在时抛出
     */
    private WorkflowTask loadTask(Long id) {
        WorkflowTask task = workflowTaskMapper.selectById(id);
        if (task == null || Integer.valueOf(DELETED).equals(task.getIsDeleted())) {
            throw new BizException("工作流任务不存在");
        }
        return task;
    }

    /**
     * 解析任务类型字符串，校验是否为系统支持的白名单类型。
     *
     * @param taskType 任务类型字符串
     * @return 枚举类型
     * @throws BizException 类型不合法时抛出
     */
    private WorkflowTaskType parseTaskType(String taskType) {
        try {
            return WorkflowTaskType.valueOf(requireText(taskType, "任务类型不能为空"));
        } catch (Exception exception) {
            throw new BizException("不支持的工作流任务类型：" + taskType);
        }
    }

    /**
     * 构建任务编号，格式为 TASK_TYPE-时间戳-UUID前8位。
     *
     * @param taskType 任务类型
     * @return 任务编号
     */
    private String buildTaskNo(WorkflowTaskType taskType) {
        return taskType.name() + "-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * 计算当前时间之后指定秒数的时间点。
     *
     * @param seconds 秒数
     * @return 未来时间点
     */
    private Date afterSeconds(int seconds) {
        return new Date(System.currentTimeMillis() + seconds * 1000L);
    }

    /**
     * 提取异常简短信息，限制长度不超过 1000 字符。
     *
     * @param exception 异常对象
     * @return 简短错误信息
     */
    private String shortError(Exception exception) {
        String message = exception == null ? "未知异常" : exception.getMessage();
        if (!StringUtils.hasText(message)) {
            message = exception.getClass().getSimpleName();
        }
        return message.length() > 1000 ? message.substring(0, 1000) : message;
    }

    /**
     * 安全转换 Integer，null 返回 0。
     *
     * @param value 原始值
     * @return 非空整型
     */
    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    /**
     * 安全获取正整数，非法值返回默认值。
     *
     * @param value        原始值
     * @param defaultValue 默认值
     * @return 正整数
     */
    private int safePositive(Integer value, int defaultValue) {
        return value == null || value <= 0 ? defaultValue : value;
    }

    /**
     * 安全获取页码，非法值返回默认页码。
     *
     * @param pageNum 原始页码
     * @return 安全页码
     */
    private long safePageNum(Long pageNum) {
        return pageNum == null || pageNum <= 0 ? DEFAULT_PAGE_NUM : pageNum;
    }

    /**
     * 安全获取页大小，非法值返回默认页大小，超过上限则截断。
     *
     * @param pageSize 原始页大小
     * @return 安全页大小
     */
    private long safePageSize(Long pageSize) {
        if (pageSize == null || pageSize <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }

    /**
     * 要求字符串非空，否则抛出业务异常。
     *
     * @param value   待检查字符串
     * @param message 为空时的异常提示
     * @return 去空白后的字符串
     * @throws BizException 字符串为空时抛出
     */
    private String requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new BizException(message);
        }
        return value.trim();
    }
}
