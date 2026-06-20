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
}
