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

    /**
     * 条件领取（审查 F2）：只有当前状态符合预期时才抢占执行权。
     *
     * <p>用状态条件更新实现，不需要分布式调度：并发重复请求只有一个能把
     * NEED_CLARIFICATION/FAILED 推进到 RUNNING。</p>
     *
     * @return true=领取成功
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public boolean claimForExecution(Long planId, String expectedStatus) {
        if (planId == null || expectedStatus == null) {
            return false;
        }
        /*
         * 领取即进入 RUNNING：后续 completeExecution 以 RUNNING 为条件写回，
         * 语义一致（"执行中"才允许落终态），也避免 PLANNED 同时表示"未开始/已领取"。
         */
        int rows = agentPlanMapper.update(null,
                new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<AgentPlan>()
                        .eq("id", planId)
                        .eq("status", expectedStatus)
                        .set("status", AgentPlanStatus.RUNNING.name())
                        .set("update_time", new Date()));
        return rows > 0;
    }

    /**
     * 条件完成（审查 F2/F5）：只有仍是 RUNNING 才写终态，取消/新执行不会被迟到结果覆盖。
     *
     * @return true=写回成功；false=状态已被改变（取消或新执行接管）
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public boolean completeExecution(Long planId, AgentPlanStatus status, String failReason,
                                     String pendingQuestion, String finalAnswer) {
        if (planId == null) {
            return false;
        }
        int rows = agentPlanMapper.update(null,
                new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<AgentPlan>()
                        .eq("id", planId)
                        .eq("status", AgentPlanStatus.RUNNING.name())
                        .set("status", status.name())
                        .set("fail_reason", failReason)
                        .set("pending_question", pendingQuestion)
                        .set("final_answer", finalAnswer)
                        .set("update_time", new Date()));
        return rows > 0;
    }

    /** 保存已消耗执行耗时（审查 F1：恢复时延续）。 */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void updateExecutionMillis(Long planId, long millis) {
        if (planId == null) {
            return;
        }
        AgentPlan plan = new AgentPlan();
        plan.setId(planId);
        plan.setExecutionMillisUsed((int) Math.max(0, millis));
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
    public void updateConstraints(Long planId, String constraintsJson) {
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
