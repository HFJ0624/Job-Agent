package com.job.bootstrap.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.job.bootstrap.mapper.AgentPlanMapper;
import com.job.bootstrap.mapper.AgentPlanStepMapper;
import com.job.bootstrap.service.AdminAgentPlanService;
import com.job.common.dto.agent.AgentPlanQueryDTO;
import com.job.common.entity.agent.AgentPlan;
import com.job.common.entity.agent.AgentPlanStep;
import com.job.common.vo.agent.AgentPlanStepVO;
import com.job.common.vo.agent.AgentPlanVO;
import com.job.exception.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 作者:hfj
 * 功能:后台 Agent 计划查询服务实现
 * 日期:2026/6/19
 */
@Service
@RequiredArgsConstructor
public class AdminAgentPlanServiceImpl implements AdminAgentPlanService {

    private static final int NOT_DELETED = 0;

    private final AgentPlanMapper agentPlanMapper;
    private final AgentPlanStepMapper agentPlanStepMapper;

    /**
     * 分页查询 Agent 计划。
     */
    @Override
    public IPage<AgentPlanVO> pagePlans(AgentPlanQueryDTO query) {
        long pageNum = query.getPageNum() == null || query.getPageNum() <= 0 ? 1L : query.getPageNum();
        long pageSize = query.getPageSize() == null || query.getPageSize() <= 0 ? 10L : query.getPageSize();
        if (pageSize > 100) {
            pageSize = 100;
        }

        Page<AgentPlan> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<AgentPlan> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AgentPlan::getIsDeleted, NOT_DELETED);

        if (StringUtils.hasText(query.getTraceId())) {
            wrapper.like(AgentPlan::getTraceId, query.getTraceId().trim());
        }
        if (query.getUserId() != null) {
            wrapper.eq(AgentPlan::getUserId, query.getUserId());
        }
        if (query.getConversationId() != null) {
            wrapper.eq(AgentPlan::getConversationId, query.getConversationId());
        }
        if (StringUtils.hasText(query.getIntentCode())) {
            wrapper.eq(AgentPlan::getIntentCode, query.getIntentCode().trim());
        }
        if (StringUtils.hasText(query.getStatus())) {
            wrapper.eq(AgentPlan::getStatus, query.getStatus().trim());
        }
        if (StringUtils.hasText(query.getStartTime())) {
            wrapper.ge(AgentPlan::getCreateTime, query.getStartTime().trim());
        }
        if (StringUtils.hasText(query.getEndTime())) {
            wrapper.le(AgentPlan::getCreateTime, query.getEndTime().trim());
        }

        wrapper.orderByDesc(AgentPlan::getCreateTime);
        IPage<AgentPlan> entityPage = agentPlanMapper.selectPage(page, wrapper);
        return entityPage.convert(AgentPlanVO::from);
    }

    /**
     * 查询计划详情。
     */
    @Override
    public AgentPlanVO getDetail(Long id) {
        AgentPlan plan = agentPlanMapper.selectById(id);
        if (plan == null || Integer.valueOf(1).equals(plan.getIsDeleted())) {
            throw new BizException("Agent 计划不存在");
        }

        List<AgentPlanStep> steps = agentPlanStepMapper.selectList(
                new LambdaQueryWrapper<AgentPlanStep>()
                        .eq(AgentPlanStep::getPlanId, id)
                        .eq(AgentPlanStep::getIsDeleted, NOT_DELETED)
                        .orderByAsc(AgentPlanStep::getStepNo)
        );

        AgentPlanVO vo = AgentPlanVO.from(plan);
        vo.setSteps(steps.stream().map(AgentPlanStepVO::from).toList());
        return vo;
    }
}
