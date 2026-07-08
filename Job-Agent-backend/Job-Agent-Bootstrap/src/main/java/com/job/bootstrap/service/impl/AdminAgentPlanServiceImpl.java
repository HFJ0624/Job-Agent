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
 * 后台 Agent 计划查询服务实现。
 *
 * <p>核心职责：为后台运营人员提供 Agent 执行计划（Plan）及其步骤（Step）的只读查询能力，
 * 支持按 Trace ID、用户 ID、会话 ID、意图编码、状态及时间范围等多维度分页检索，
 * 帮助管理员追踪 Agent 单次调用的完整执行链路。</p>
 *
 * <p>所属业务模块：Agent 运营中心 - 计划查询</p>
 *
 * <p>主要调用链：
 * AdminAgentPlanController → {@link AdminAgentPlanServiceImpl} →
 * AgentPlanMapper / AgentPlanStepMapper → 返回 AgentPlanVO / AgentPlanStepVO</p>
 *
 * <p>与其他核心组件的关系：
 * <ul>
 *   <li>依赖 {@link AgentPlanMapper} 读取 Agent 计划基础数据</li>
 *   <li>依赖 {@link AgentPlanStepMapper} 读取计划下的执行步骤明细</li>
 * </ul></p>
 *
 * <p>设计说明：
 * <ul>
 *   <li>本服务只提供查询能力，不暴露修改接口，避免后台误操作影响 Agent 执行历史。</li>
 *   <li>计划详情查询会自动聚合所属步骤，按步骤序号升序排列，直观展示执行顺序。</li>
 *   <li>分页参数做防御性处理，限制最大 pageSize 为 100。</li>
 * </ul></p>
 *
 * @author hfj
 * @since 2026/6/19
 */
@Service
@RequiredArgsConstructor
public class AdminAgentPlanServiceImpl implements AdminAgentPlanService {

    private static final int NOT_DELETED = 0;

    private final AgentPlanMapper agentPlanMapper;
    private final AgentPlanStepMapper agentPlanStepMapper;

    /**
     * 分页查询 Agent 执行计划。
     *
     * <p>方法步骤：</p>
     * <ol>
     *   <li>防御性处理分页参数，限制最大 pageSize 为 100。</li>
     *   <li>动态拼接查询条件，支持 Trace ID、用户 ID、会话 ID、意图编码、状态及时间范围。</li>
     *   <li>按创建时间倒序分页查询，返回 Plan VO 分页。</li>
     * </ol>
     *
     * @param query 计划查询条件，包含分页及多维度过滤参数
     * @return Agent 计划 VO 分页结果
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
     * 查询 Agent 计划详情，包含所属步骤列表。
     *
     * <p>方法步骤：</p>
     * <ol>
     *   <li>按 ID 查询计划实体，校验未删除状态。</li>
     *   <li>查询该计划下全部未删除的步骤，按步骤序号升序排列。</li>
     *   <li>将计划与步骤列表组装为 VO 返回。</li>
     * </ol>
     *
     * @param id 计划主键 ID
     * @return 包含步骤明细的计划详情 VO
     * @throws BizException 当计划不存在或已删除时抛出
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
