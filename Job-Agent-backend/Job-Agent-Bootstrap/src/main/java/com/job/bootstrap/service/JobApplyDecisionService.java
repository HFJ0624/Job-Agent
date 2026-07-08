package com.job.bootstrap.service;

import com.job.common.vo.decision.JobApplyDecisionVO;

/**
 * AI 投递决策服务。
 *
 * <p>核心职责：基于 AI 能力分析用户简历与目标岗位的匹配度，生成投递建议决策（如强烈推荐、谨慎投递、不建议投递等），辅助用户做出更科学的求职投递选择。</p>
 *
 * <p>所属业务模块：求职决策 / AI 投递建议</p>
 *
 * <p>主要调用链：Front Controller → JobApplyDecisionService → LLM 决策引擎 / 简历解析 Service / 岗位解析 Service</p>
 */
public interface JobApplyDecisionService {

    /**
     * 为用户、简历、岗位组合生成一条新的 AI 投递决策。
     *
     * @param userId   当前用户 ID
     * @param resumeId 用户简历 ID
     * @param jobId    目标岗位 ID
     * @return AI 投递决策结果，包含匹配度评分、决策建议、优势与风险提示
     */
    JobApplyDecisionVO generateDecision(Long userId, Long resumeId, Long jobId);

    /**
     * 查询指定用户、简历、岗位组合下最近一次的 AI 投递决策。
     *
     * @param userId   当前用户 ID
     * @param resumeId 用户简历 ID
     * @param jobId    目标岗位 ID
     * @return 最近一次 AI 投递决策，无记录时返回空
     */
    JobApplyDecisionVO getLatestDecision(Long userId, Long resumeId, Long jobId);
}
