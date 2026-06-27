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

    private WorkflowTask loadTask(Long id) {
        WorkflowTask task = workflowTaskMapper.selectById(id);
        if (task == null || Integer.valueOf(DELETED).equals(task.getIsDeleted())) {
            throw new BizException("工作流任务不存在");
        }
        return task;
    }

    private WorkflowTaskType parseTaskType(String taskType) {
        try {
            return WorkflowTaskType.valueOf(requireText(taskType, "任务类型不能为空"));
        } catch (Exception exception) {
            throw new BizException("不支持的工作流任务类型：" + taskType);
        }
    }

    private String buildTaskNo(WorkflowTaskType taskType) {
        return taskType.name() + "-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private Date afterSeconds(int seconds) {
        return new Date(System.currentTimeMillis() + seconds * 1000L);
    }

    private String shortError(Exception exception) {
        String message = exception == null ? "未知异常" : exception.getMessage();
        if (!StringUtils.hasText(message)) {
            message = exception.getClass().getSimpleName();
        }
        return message.length() > 1000 ? message.substring(0, 1000) : message;
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private int safePositive(Integer value, int defaultValue) {
        return value == null || value <= 0 ? defaultValue : value;
    }

    private long safePageNum(Long pageNum) {
        return pageNum == null || pageNum <= 0 ? DEFAULT_PAGE_NUM : pageNum;
    }

    private long safePageSize(Long pageSize) {
        if (pageSize == null || pageSize <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }

    private String requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new BizException(message);
        }
        return value.trim();
    }
}
