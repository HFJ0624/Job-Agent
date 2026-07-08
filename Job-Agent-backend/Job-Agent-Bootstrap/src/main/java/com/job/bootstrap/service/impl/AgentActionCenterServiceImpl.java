package com.job.bootstrap.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.job.bootstrap.mapper.AgentActionItemMapper;
import com.job.bootstrap.mapper.WorkflowTaskMapper;
import com.job.bootstrap.service.AgentActionCenterService;
import com.job.common.dto.agent.AgentActionItemStatusDTO;
import com.job.common.entity.agent.AgentActionItem;
import com.job.common.entity.workflow.WorkflowTask;
import com.job.common.vo.agent.AgentActionItemVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;

/**
 * Agent 行动确认中心服务实现，负责行动项状态机与执行编排。
 *
 * <p>核心职责：
 * 管理 Agent 生成的行动项全生命周期（PENDING/DONE/IGNORED/SNOOZED/FAILED），
 * 在用户确认完成时调用 AgentActionExecutor 联动业务服务，
 * 在查询时聚合 WorkflowTask 快照供前端展示异步任务进度。</p>
 *
 * <p>所属业务模块：Job-Agent-Bootstrap 模块下的 Agent Action 子模块（行动确认中心）。</p>
 *
 * <p>主要调用链：
 * 前端确认/忽略/稍后处理 -> AgentActionCenterService
 * -> getUserActionRequired（权限校验）
 * -> AgentActionExecutor.execute（联动业务服务）
 * -> actionItemMapper.updateById（状态回写）
 * listPending -> 查询 PENDING/FAILED/SNOOZED 已到期行动项 + fillWorkflowTaskSnapshot</p>
 *
 * <p>与其他核心组件的关系：
 * <ul>
 *   <li>V1 只管理行动项自身状态，不联动原业务表；V2 通过 AgentActionExecutor 联动业务服务；</li>
 *   <li>稍后处理的行动项在 snoozeUntil 到期前不展示，避免用户反复看到已推迟的建议；</li>
 *   <li>所有状态更新都校验 userId，防止用户修改别人的行动项；</li>
 *   <li>markDone 使用 noRollbackFor=Exception，让 FAILED 状态能正常落库，方便用户重试。</li>
 * </ul></p>
 *
 * <p>设计说明：
 * 1. V1 只管理行动项自身状态，不联动原业务表。
 * 2. 稍后处理的行动项在 snoozeUntil 到期前不展示，避免用户反复看到已推迟的建议。
 * 3. 所有状态更新都校验 userId，防止用户修改别人的行动项。</p>
 *
 * 作者: hfj
 */
@Service
@RequiredArgsConstructor
public class AgentActionCenterServiceImpl implements AgentActionCenterService {

    private static final int NOT_DELETED = 0;
    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_DONE = "DONE";
    private static final String STATUS_IGNORED = "IGNORED";
    private static final String STATUS_SNOOZED = "SNOOZED";
    private static final String STATUS_FAILED = "FAILED";
    private static final String ACTION_MANUAL_CONFIRM = "MANUAL_CONFIRM";

    private final AgentActionItemMapper actionItemMapper;
    private final WorkflowTaskMapper workflowTaskMapper;
    private final AgentActionExecutor actionExecutor;

    /**
     * 列出当前用户待处理的行动项，包含 PENDING、FAILED 与已到期 SNOOZED。
     *
     * <p>核心处理流程：
     * 1. 对 limit 做 [1,50] 范围裁剪，避免过大查询拖慢数据库；
     * 2. 查询 userId 维度下未删除且状态属于 PENDING / FAILED / SNOOZED 已到期的行动项；
     * 3. 按 createTime 升序排列，优先展示最早产生的建议；
     * 4. 通过 toVO 转换并补充 WorkflowTask 快照，让前端展示异步任务进度。</p>
     *
     * @param userId 当前用户 ID，用于过滤归属
     * @param limit  期望返回的最大条数，会被裁剪到 [1,50]
     * @return 待处理行动项 VO 列表，已聚合 WorkflowTask 快照
     */
    @Override
    public List<AgentActionItemVO> listPending(Long userId, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 50));
        Date now = new Date();
        return actionItemMapper.selectList(
                        new LambdaQueryWrapper<AgentActionItem>()
                                .eq(AgentActionItem::getUserId, userId)
                                .eq(AgentActionItem::getIsDeleted, NOT_DELETED)
                                .and(wrapper -> wrapper
                                        .eq(AgentActionItem::getActionStatus, STATUS_PENDING)
                                        .or()
                                        .eq(AgentActionItem::getActionStatus, STATUS_FAILED)
                                        .or(query -> query
                                                .eq(AgentActionItem::getActionStatus, STATUS_SNOOZED)
                                                .le(AgentActionItem::getSnoozeUntil, now)))
                                .orderByAsc(AgentActionItem::getCreateTime)
                                .last("limit " + safeLimit)
                )
                .stream()
                .map(this::toVO)
                .toList();
    }

    /**
     * 标记行动项为已完成，必要时联动业务服务执行真实动作。
     *
     * <p>核心处理流程：
     * 1. 校验行动项归属当前用户，防止越权操作他人行动项；
     * 2. 非 MANUAL_CONFIRM 类型先调用 AgentActionExecutor.execute 联动业务服务；
     * 3. 执行成功则更新状态为 DONE、记录完成时间与备注，回写 workflowTaskId；
     * 4. 执行失败则更新状态为 FAILED、记录 executeError，并重新抛出异常让上层感知。
     *    noRollbackFor=Exception 保证 FAILED 状态能落库，方便用户重试。</p>
     *
     * @param userId   当前用户 ID，用于权限校验
     * @param actionId 行动项 ID
     * @param dto      状态更新 DTO，包含 note 等附加信息
     * @throws IllegalArgumentException 行动项不存在或不归属当前用户时抛出
     * @throws RuntimeException         AgentActionExecutor 执行失败时抛出，状态已落库为 FAILED
     */
    @Override
    @Transactional(noRollbackFor = Exception.class)
    public void markDone(Long userId, Long actionId, AgentActionItemStatusDTO dto) {
        AgentActionItem item = getUserActionRequired(userId, actionId);

        /*
         * V2：可执行 actionType 先联动业务服务；旧的 MANUAL_CONFIRM 仍只标记完成。
         */
        try {
            if (!ACTION_MANUAL_CONFIRM.equals(item.getActionType())) {
                Long workflowTaskId = actionExecutor.execute(item);
                if (workflowTaskId != null) {
                    item.setWorkflowTaskId(workflowTaskId);
                }
            }
            item.setActionStatus(STATUS_DONE);
            item.setExecuteError(null);
            item.setDoneTime(new Date());
            item.setNote(dto == null ? null : dto.getNote());
            item.setUpdateTime(new Date());
            actionItemMapper.updateById(item);
        } catch (Exception exception) {
            item.setActionStatus(STATUS_FAILED);
            item.setExecuteError(shortText(exception.getMessage(), 1000));
            item.setNote(dto == null ? null : dto.getNote());
            item.setUpdateTime(new Date());
            actionItemMapper.updateById(item);
            throw exception;
        }
    }

    /**
     * 忽略行动项，仅更新状态为 IGNORED，不联动业务服务。
     *
     * @param userId   当前用户 ID，用于权限校验
     * @param actionId 行动项 ID
     * @param dto      状态更新 DTO，包含 note 等附加信息
     * @throws IllegalArgumentException 行动项不存在或不归属当前用户时抛出
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void ignore(Long userId, Long actionId, AgentActionItemStatusDTO dto) {
        AgentActionItem item = getUserActionRequired(userId, actionId);
        item.setActionStatus(STATUS_IGNORED);
        item.setNote(dto == null ? null : dto.getNote());
        item.setUpdateTime(new Date());
        actionItemMapper.updateById(item);
    }

    /**
     * 推迟行动项到指定时间后再展示，避免用户反复看到已推迟的建议。
     *
     * @param userId   当前用户 ID，用于权限校验
     * @param actionId 行动项 ID
     * @param dto      状态更新 DTO，必须包含 snoozeUntil
     * @throws IllegalArgumentException snoozeUntil 为空或行动项不存在时抛出
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void snooze(Long userId, Long actionId, AgentActionItemStatusDTO dto) {
        if (dto == null || dto.getSnoozeUntil() == null) {
            throw new IllegalArgumentException("稍后处理时间不能为空");
        }

        AgentActionItem item = getUserActionRequired(userId, actionId);
        item.setActionStatus(STATUS_SNOOZED);
        item.setSnoozeUntil(dto.getSnoozeUntil());
        item.setNote(dto.getNote());
        item.setUpdateTime(new Date());
        actionItemMapper.updateById(item);
    }

    /**
     * 查询并校验行动项归属当前用户，缺失或不归属时抛 IllegalArgumentException。
     *
     * @param userId   当前用户 ID
     * @param actionId 行动项 ID
     * @return 行动项实体
     * @throws IllegalArgumentException 行动项不存在或不归属当前用户时抛出
     */
    private AgentActionItem getUserActionRequired(Long userId, Long actionId) {
        AgentActionItem item = actionItemMapper.selectOne(
                new LambdaQueryWrapper<AgentActionItem>()
                        .eq(AgentActionItem::getId, actionId)
                        .eq(AgentActionItem::getUserId, userId)
                        .eq(AgentActionItem::getIsDeleted, NOT_DELETED)
                        .last("limit 1")
        );
        if (item == null) {
            throw new IllegalArgumentException("行动项不存在");
        }
        return item;
    }

    /**
     * 将行动项实体转换为 VO，并填充 WorkflowTask 快照字段。
     *
     * @param item 行动项实体
     * @return 行动项 VO，包含基础字段与异步任务快照
     */
    private AgentActionItemVO toVO(AgentActionItem item) {
        AgentActionItemVO vo = new AgentActionItemVO();
        vo.setId(item.getId());
        vo.setUserId(item.getUserId());
        vo.setActionKey(item.getActionKey());
        vo.setSourceType(item.getSourceType());
        vo.setSourceId(item.getSourceId());
        vo.setActionType(item.getActionType());
        vo.setBizType(item.getBizType());
        vo.setBizId(item.getBizId());
        vo.setActionTitle(item.getActionTitle());
        vo.setActionDesc(item.getActionDesc());
        vo.setPriority(item.getPriority());
        vo.setActionStatus(item.getActionStatus());
        vo.setTargetPath(item.getTargetPath());
        vo.setActionPayload(item.getActionPayload());
        vo.setExecuteError(item.getExecuteError());
        vo.setWorkflowTaskId(item.getWorkflowTaskId());
        fillWorkflowTaskSnapshot(vo, item.getWorkflowTaskId());
        vo.setSnoozeUntil(item.getSnoozeUntil());
        vo.setNote(item.getNote());
        vo.setDoneTime(item.getDoneTime());
        vo.setCreateTime(item.getCreateTime());
        vo.setUpdateTime(item.getUpdateTime());
        return vo;
    }

    /**
     * 填充行动项 VO 中的 WorkflowTask 快照字段，让前端展示异步任务最新状态。
     *
     * <p>核心处理流程：
     * 1. workflowTaskId 为空时直接返回；
     * 2. 查询未删除的工作流任务，避免行动项保存过期状态；
     * 3. 任务不存在时只保留 workflowTaskId，前端仍能知道曾经关联过异步任务；
     * 4. 任务存在时回填任务编号、状态、进度、当前步骤与错误信息。</p>
     *
     * @param vo             行动项 VO
     * @param workflowTaskId 关联的工作流任务 ID
     */
    private void fillWorkflowTaskSnapshot(AgentActionItemVO vo, Long workflowTaskId) {
        if (workflowTaskId == null) {
            return;
        }
        /*
         * 步骤：
         * 1. 行动项只保存 workflowTaskId，避免复制一份易过期的任务状态。
         * 2. 查询列表时读取任务快照，让用户看到最新状态、进度和失败原因。
         * 3. 如果任务被删除或不存在，只保留 workflowTaskId，前端仍能知道曾经关联过异步任务。
         */
        WorkflowTask task = workflowTaskMapper.selectOne(
                new LambdaQueryWrapper<WorkflowTask>()
                        .eq(WorkflowTask::getId, workflowTaskId)
                        .eq(WorkflowTask::getIsDeleted, NOT_DELETED)
                        .last("limit 1")
        );
        if (task == null) {
            return;
        }
        vo.setWorkflowTaskNo(task.getTaskNo());
        vo.setWorkflowTaskStatus(task.getStatus());
        vo.setWorkflowTaskProgress(task.getProgressPercent());
        vo.setWorkflowTaskStep(task.getCurrentStep());
        vo.setWorkflowTaskError(task.getErrorMsg());
    }

    private String shortText(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String text = value.trim();
        return text.length() <= maxLength ? text : text.substring(0, maxLength);
    }
}
