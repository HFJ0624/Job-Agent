package com.job.bootstrap.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.job.bootstrap.mapper.AgentActionItemMapper;
import com.job.bootstrap.mapper.WorkflowTaskMapper;
import com.job.bootstrap.service.AdminAgentActionItemService;
import com.job.common.dto.agent.AgentActionItemQueryDTO;
import com.job.common.entity.agent.AgentActionItem;
import com.job.common.entity.workflow.WorkflowTask;
import com.job.common.vo.agent.AgentActionItemVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Admin Agent 行动项管理服务实现。
 *
 * 说明：
 * 1. 第一版只做只读分页和排查，不提供后台强制修改行动项状态，避免管理员误操作用户行动。
 * 2. 行动项只保存 workflowTaskId，列表展示时读取工作流快照，保证状态尽量实时。
 */
@Service
@RequiredArgsConstructor
public class AdminAgentActionItemServiceImpl implements AdminAgentActionItemService {

    private static final int NOT_DELETED = 0;
    private static final long DEFAULT_PAGE_NUM = 1L;
    private static final long DEFAULT_PAGE_SIZE = 10L;
    private static final long MAX_PAGE_SIZE = 100L;

    private final AgentActionItemMapper actionItemMapper;
    private final WorkflowTaskMapper workflowTaskMapper;

    @Override
    public IPage<AgentActionItemVO> pageActions(AgentActionItemQueryDTO query) {
        AgentActionItemQueryDTO safeQuery = query == null ? new AgentActionItemQueryDTO() : query;
        Page<AgentActionItem> page = new Page<>(safePageNum(safeQuery.getPageNum()), safePageSize(safeQuery.getPageSize()));

        LambdaQueryWrapper<AgentActionItem> wrapper = new LambdaQueryWrapper<AgentActionItem>()
                .eq(AgentActionItem::getIsDeleted, NOT_DELETED);
        if (safeQuery.getUserId() != null) {
            wrapper.eq(AgentActionItem::getUserId, safeQuery.getUserId());
        }
        if (StringUtils.hasText(safeQuery.getSourceType())) {
            wrapper.eq(AgentActionItem::getSourceType, safeQuery.getSourceType().trim());
        }
        if (StringUtils.hasText(safeQuery.getActionType())) {
            wrapper.eq(AgentActionItem::getActionType, safeQuery.getActionType().trim());
        }
        if (StringUtils.hasText(safeQuery.getActionStatus())) {
            wrapper.eq(AgentActionItem::getActionStatus, safeQuery.getActionStatus().trim());
        }
        if (Boolean.TRUE.equals(safeQuery.getFailedOnly())) {
            wrapper.and(item -> item
                    .isNotNull(AgentActionItem::getExecuteError)
                    .or()
                    .eq(AgentActionItem::getActionStatus, "FAILED"));
        }
        if (Boolean.TRUE.equals(safeQuery.getHasWorkflowTask())) {
            wrapper.isNotNull(AgentActionItem::getWorkflowTaskId);
        }
        if (Boolean.FALSE.equals(safeQuery.getHasWorkflowTask())) {
            wrapper.isNull(AgentActionItem::getWorkflowTaskId);
        }
        if (safeQuery.getWorkflowTaskId() != null) {
            wrapper.eq(AgentActionItem::getWorkflowTaskId, safeQuery.getWorkflowTaskId());
        }
        if (StringUtils.hasText(safeQuery.getKeyword())) {
            String keyword = safeQuery.getKeyword().trim();
            wrapper.and(item -> item
                    .like(AgentActionItem::getActionTitle, keyword)
                    .or()
                    .like(AgentActionItem::getActionDesc, keyword)
                    .or()
                    .like(AgentActionItem::getActionPayload, keyword));
        }
        wrapper.orderByDesc(AgentActionItem::getCreateTime);

        return actionItemMapper.selectPage(page, wrapper).convert(this::toVO);
    }

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
        vo.setSnoozeUntil(item.getSnoozeUntil());
        vo.setNote(item.getNote());
        vo.setDoneTime(item.getDoneTime());
        vo.setCreateTime(item.getCreateTime());
        vo.setUpdateTime(item.getUpdateTime());
        fillWorkflowTaskSnapshot(vo, item.getWorkflowTaskId());
        return vo;
    }

    private void fillWorkflowTaskSnapshot(AgentActionItemVO vo, Long workflowTaskId) {
        if (workflowTaskId == null) {
            return;
        }
        /*
         * 步骤：
         * 1. 用 workflowTaskId 读取工作流任务快照。
         * 2. 只填充展示字段，不修改行动项本身，避免读接口产生副作用。
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

    private long safePageNum(Long pageNum) {
        return pageNum == null || pageNum < 1 ? DEFAULT_PAGE_NUM : pageNum;
    }

    private long safePageSize(Long pageSize) {
        if (pageSize == null || pageSize < 1) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }
}
