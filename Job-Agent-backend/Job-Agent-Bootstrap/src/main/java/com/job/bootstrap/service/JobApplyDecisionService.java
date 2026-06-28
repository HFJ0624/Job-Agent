package com.job.bootstrap.service;

import com.job.common.vo.decision.JobApplyDecisionVO;

/**
 * 功能: AI 投递决策服务。
 */
public interface JobApplyDecisionService {

    /**
     * 生成一条新的 AI 投递决策。
     */
    JobApplyDecisionVO generateDecision(Long userId, Long resumeId, Long jobId);

    /**
     * 查询最近一次 AI 投递决策。
     */
    JobApplyDecisionVO getLatestDecision(Long userId, Long resumeId, Long jobId);
}
