package com.job.bootstrap.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.job.bootstrap.mapper.AgentActionItemMapper;
import com.job.bootstrap.service.AgentActionCenterService;
import com.job.common.dto.agent.AgentActionItemStatusDTO;
import com.job.common.entity.agent.AgentActionItem;
import com.job.common.vo.agent.AgentActionItemVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;

/**
 * Agent 行动确认中心服务实现。
 *
 * 说明：
 * 1. V1 只管理行动项自身状态，不联动原业务表。
 * 2. 稍后处理的行动项在 snoozeUntil 到期前不展示，避免用户反复看到已推迟的建议。
 * 3. 所有状态更新都校验 userId，防止用户修改别人的行动项。
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
    private final AgentActionExecutor actionExecutor;

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

    @Override
    @Transactional(noRollbackFor = Exception.class)
    public void markDone(Long userId, Long actionId, AgentActionItemStatusDTO dto) {
        AgentActionItem item = getUserActionRequired(userId, actionId);

        /*
         * V2：可执行 actionType 先联动业务服务；旧的 MANUAL_CONFIRM 仍只标记完成。
         */
        try {
            if (!ACTION_MANUAL_CONFIRM.equals(item.getActionType())) {
                actionExecutor.execute(item);
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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void ignore(Long userId, Long actionId, AgentActionItemStatusDTO dto) {
        AgentActionItem item = getUserActionRequired(userId, actionId);
        item.setActionStatus(STATUS_IGNORED);
        item.setNote(dto == null ? null : dto.getNote());
        item.setUpdateTime(new Date());
        actionItemMapper.updateById(item);
    }

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

    private AgentActionItemVO toVO(AgentActionItem item) {
        AgentActionItemVO vo = new AgentActionItemVO();
        vo.setId(item.getId());
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
        vo.setExecuteError(item.getExecuteError());
        vo.setSnoozeUntil(item.getSnoozeUntil());
        vo.setNote(item.getNote());
        vo.setDoneTime(item.getDoneTime());
        vo.setCreateTime(item.getCreateTime());
        vo.setUpdateTime(item.getUpdateTime());
        return vo;
    }

    private String shortText(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String text = value.trim();
        return text.length() <= maxLength ? text : text.substring(0, maxLength);
    }
}
