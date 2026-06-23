package com.job.bootstrap.service;

import com.job.bootstrap.agent.intent.AgentIntentCode;
import com.job.common.vo.agent.AgentPlanVO;

/**
 * 作者:hfj
 * 功能:Agent 计划生成服务
 * 日期:2026/6/19
 */
public interface AgentPlanningService {

    /**
     * 根据用户目标生成计划并落库。
     *
     * @param userId 用户ID
     * @param conversationId 会话ID
     * @param traceId TraceId
     * @param intentCode 意图
     * @param userGoal 用户原始目标
     * @return 计划信息
     */
    AgentPlanVO createPlan(Long userId, Long conversationId, String traceId, AgentIntentCode intentCode, String userGoal);

    /**
     * 根据用户目标和长期记忆上下文生成计划。
     *
     * 说明:
     * 1. userGoal 仍然保存用户本轮原始目标，方便后台排查。
     * 2. planningContext 只参与参数抽取，不直接写入 userGoal，避免内部记忆上下文污染用户历史记录。
     *
     * @param userId 用户ID
     * @param conversationId 会话ID
     * @param traceId TraceId
     * @param intentCode 意图
     * @param userGoal 用户原始目标
     * @param planningContext 长期记忆上下文
     * @return 计划信息
     */
    AgentPlanVO createPlan(
            Long userId,
            Long conversationId,
            String traceId,
            AgentIntentCode intentCode,
            String userGoal,
            String planningContext
    );

    /**
     * 查询当前用户的一份 Agent 计划。
     *
     * @param userId 当前登录用户ID
     * @param planId 计划ID
     * @return 计划详情
     */
    AgentPlanVO getUserPlan(Long userId, Long planId);
}
