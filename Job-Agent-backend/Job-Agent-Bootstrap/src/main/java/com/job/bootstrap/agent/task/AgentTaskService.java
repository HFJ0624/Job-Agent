package com.job.bootstrap.agent.task;

import com.job.bootstrap.mapper.AgentPlanMapper;
import com.job.common.entity.agent.AgentPlan;
import com.job.enums.AgentPlanStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.Map;

/**
 * 功能: 父任务（AgentPlan）服务（第二阶段）。
 *
 * 设计说明:
 * 1. 复合任务的父计划复用 AgentPlan，创建时**固定 runtimeMode**（WORKFLOW_MULTI_AGENT）——
 *    恢复时优先按 runtimeMode 路由，开关只影响新任务；
 * 2. 写操作短事务；执行链路（模型/工具）在事务外；
 * 3. 条件快照存 extractedParamsJson（含来源与版本），供子节点计算 inputVersion。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentTaskService {

    public static final String RUNTIME_MULTI_AGENT = "WORKFLOW_MULTI_AGENT";
    public static final String RUNTIME_REACT_SEARCH = "WORKFLOW_REACT";
    public static final String RUNTIME_TEMPLATE = "TEMPLATE";

    private static final int NOT_DELETED = 0;

    private final AgentPlanMapper agentPlanMapper;

    /**
     * 创建父计划（短事务）。
     *
     * @param constraintsJson 条件快照 JSON（含版本）
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public AgentPlan createParentPlan(Long userId, Long conversationId, String traceId,
                                      String intentCode, String userGoal,
                                      String runtimeMode, String constraintsJson) {
        Date now = new Date();
        AgentPlan plan = new AgentPlan();
        plan.setTraceId(traceId);
        plan.setUserId(userId);
        plan.setConversationId(conversationId);
        plan.setIntentCode(intentCode);
        plan.setUserGoal(userGoal);
        plan.setPlanTitle("求职复合任务");
        plan.setPlanSummary("简历画像 + 岗位搜索 + 匹配报告（三 Agent 协作）");
        plan.setRequiredParamsJson("[]");
        plan.setExtractedParamsJson(constraintsJson == null ? "{}" : constraintsJson);
        plan.setMissingParamsJson("[]");
        plan.setStatus(AgentPlanStatus.PLANNED.name());
        plan.setRuntimeMode(runtimeMode);
        plan.setIsDeleted(NOT_DELETED);
        plan.setCreateTime(now);
        plan.setUpdateTime(now);
        agentPlanMapper.insert(plan);
        return plan;
    }

    /** 只读加载（仅供路由判断，不校验归属；执行入口必须用 loadOwned）。 */
    public AgentPlan load(Long planId) {
        return planId == null ? null : agentPlanMapper.selectById(planId);
    }

    /**
     * 按 ID 加载并校验归属（不属于当前用户返回 null，避免越权恢复）。
     */
    public AgentPlan loadOwned(Long userId, Long planId) {
        if (planId == null) {
            return null;
        }
        AgentPlan plan = agentPlanMapper.selectById(planId);
        if (plan == null || !userId.equals(plan.getUserId())) {
            return null;
        }
        return plan;
    }

    /**
     * 更新父计划状态（短事务）。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void updateStatus(Long planId, AgentPlanStatus status, String failReason) {
        updateStatus(planId, status, failReason, null);
    }

    /** 更新状态并可携带待补充问题（第三阶段：刷新后恢复展示）。 */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void updateStatus(Long planId, AgentPlanStatus status, String failReason, String pendingQuestion) {
        if (planId == null) {
            return;
        }
        AgentPlan plan = new AgentPlan();
        plan.setId(planId);
        plan.setStatus(status.name());
        plan.setFailReason(failReason);
        plan.setPendingQuestion(pendingQuestion);
        plan.setUpdateTime(new Date());
        agentPlanMapper.updateById(plan);
    }

    /** 保存父任务已用额度（第三阶段：补参恢复延续，不清零）。 */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void updateBudgetUsage(Long planId, int modelCallsUsed, int toolCallsUsed) {
        if (planId == null) {
            return;
        }
        AgentPlan plan = new AgentPlan();
        plan.setId(planId);
        plan.setModelCallsUsed(modelCallsUsed);
        plan.setToolCallsUsed(toolCallsUsed);
        plan.setUpdateTime(new Date());
        agentPlanMapper.updateById(plan);
    }

    /** 显式取消（第三阶段）：置 CANCELLED，保留已完成产物。 */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void markCancelled(Long planId, String reason) {
        if (planId == null) {
            return;
        }
        AgentPlan plan = new AgentPlan();
        plan.setId(planId);
        plan.setStatus(AgentPlanStatus.CANCELLED.name());
        plan.setFailReason(reason);
        plan.setCancelledAt(new Date());
        plan.setUpdateTime(new Date());
        agentPlanMapper.updateById(plan);
    }

    /**
     * 保存条件快照（补参/条件变化时更新，版本随之变化）。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void updateConstraints(Long planId, String constraintsJson, Map<String, Object> extractedParams) {
        if (planId == null) {
            return;
        }
        AgentPlan plan = new AgentPlan();
        plan.setId(planId);
        plan.setExtractedParamsJson(constraintsJson);
        plan.setUpdateTime(new Date());
        agentPlanMapper.updateById(plan);
    }
}
