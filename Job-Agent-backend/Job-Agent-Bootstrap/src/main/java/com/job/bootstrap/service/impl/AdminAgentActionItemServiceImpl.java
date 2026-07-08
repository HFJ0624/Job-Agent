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
 * <p>核心职责：为后台运营人员提供 Agent 行动项（ActionItem）的只读查询与排查能力，
 * 支持按用户、来源类型、行动类型、状态、工作流关联关系及关键词等多维度分页检索。</p>
 *
 * <p>所属业务模块：Agent 运营中心 - 行动项管理</p>
 *
 * <p>主要调用链：
 * AdminAgentActionItemController → {@link AdminAgentActionItemServiceImpl#pageActions} →
 * AgentActionItemMapper / WorkflowTaskMapper → 返回 AgentActionItemVO 分页</p>
 *
 * <p>与其他核心组件的关系：
 * <ul>
 *   <li>依赖 {@link AgentActionItemMapper} 读取行动项基础数据</li>
 *   <li>依赖 {@link WorkflowTaskMapper} 实时拉取工作流任务快照，补充展示字段</li>
 * </ul></p>
 *
 * <p>设计说明：
 * <ul>
 *   <li>第一版只做只读分页和排查，不提供后台强制修改行动项状态，避免管理员误操作用户行动。</li>
 *   <li>行动项只保存 workflowTaskId，列表展示时读取工作流快照，保证状态尽量实时。</li>
 *   <li>分页参数做防御性处理，限制最大 pageSize 为 100，防止单次查询拖垮数据库。</li>
 * </ul></p>
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

    /**
     * 分页查询 Agent 行动项。
     *
     * <p>方法步骤：</p>
     * <ol>
     *   <li>对分页参数进行防御性处理，空查询时自动创建默认对象。</li>
     *   <li>根据查询条件动态拼接 LambdaQueryWrapper，支持用户 ID、来源类型、行动类型、
     *       执行状态、失败筛选、工作流关联关系及关键词模糊匹配。</li>
     *   <li>按创建时间倒序执行分页查询，保证最新行动项优先展示。</li>
     *   <li>结果通过 {@link #toVO} 转换为 VO，并填充工作流快照。</li>
     * </ol>
     *
     * @param query 行动项查询条件，包含分页、过滤及关键词参数
     * @return 带工作流快照的行动项 VO 分页结果
     */
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

    /**
     * 将 AgentActionItem 实体转换为展示 VO。
     *
     * <p>逐字段拷贝基础属性后，调用 {@link #fillWorkflowTaskSnapshot} 补充工作流实时快照，
     * 保证管理员看到的状态与当前工作流进度保持一致。</p>
     *
     * @param item 行动项持久化实体
     * @return 包含工作流快照的展示 VO
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
        vo.setSnoozeUntil(item.getSnoozeUntil());
        vo.setNote(item.getNote());
        vo.setDoneTime(item.getDoneTime());
        vo.setCreateTime(item.getCreateTime());
        vo.setUpdateTime(item.getUpdateTime());
        fillWorkflowTaskSnapshot(vo, item.getWorkflowTaskId());
        return vo;
    }

    /**
     * 填充工作流任务快照到行动项 VO。
     *
     * <p>方法步骤：</p>
     * <ol>
     *   <li>用 workflowTaskId 读取未删除的工作流任务快照。</li>
     *   <li>只填充展示字段（任务编号、状态、进度、当前步骤、错误信息），
     *       不修改行动项本身，避免读接口产生副作用。</li>
     * </ol>
     *
     * @param vo 待填充的行动项展示 VO
     * @param workflowTaskId 关联的工作流任务 ID
     */
    private void fillWorkflowTaskSnapshot(AgentActionItemVO vo, Long workflowTaskId) {
        if (workflowTaskId == null) {
            return;
        }
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

    /**
     * 防御性处理页码参数，防止空值或非法页码导致查询异常。
     *
     * @param pageNum 前端传入的页码
     * @return 安全的页码，空值或小于 1 时返回默认值 1
     */
    private long safePageNum(Long pageNum) {
        return pageNum == null || pageNum < 1 ? DEFAULT_PAGE_NUM : pageNum;
    }

    /**
     * 防御性处理每页大小参数，限制上限避免单次查询过大拖垮性能。
     *
     * @param pageSize 前端传入的每页大小
     * @return 安全的每页大小，空值或小于 1 时返回默认值 10，最大不超过 100
     */
    private long safePageSize(Long pageSize) {
        if (pageSize == null || pageSize < 1) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }
}
