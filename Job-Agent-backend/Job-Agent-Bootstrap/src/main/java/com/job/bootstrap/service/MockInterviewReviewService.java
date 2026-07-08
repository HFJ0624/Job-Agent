package com.job.bootstrap.service;

import com.job.common.vo.interview.MockInterviewReviewVO;
import com.job.common.vo.interview.MockInterviewStudyPlanVO;

/**
 * 模拟面试复盘报告服务。
 *
 * <p>核心职责：为模拟面试提供 AI 复盘能力，基于会话答题记录生成综合能力评估报告，并支持根据复盘结果生成针对性补课学习计划。</p>
 *
 * <p>所属业务模块：面试辅助 / 模拟面试复盘</p>
 *
 * <p>主要调用链：Front/Admin Controller → MockInterviewReviewService → LLM 复盘引擎 / 模拟面试记录 Service / 学习计划 Service</p>
 */
public interface MockInterviewReviewService {

    /**
     * 为指定模拟面试会话生成 AI 复盘报告。
     *
     * @param userId    当前用户 ID
     * @param sessionId 模拟面试会话 ID
     * @return 复盘报告，包含综合评分、各能力维度分析、答题点评、改进建议
     */
    MockInterviewReviewVO generateReview(Long userId, Long sessionId);

    /**
     * 查询某轮模拟面试最近一次复盘报告。
     *
     * @param userId    当前用户 ID
     * @param sessionId 模拟面试会话 ID
     * @return 最近一次复盘报告，无记录时返回空
     */
    MockInterviewReviewVO getLatestReview(Long userId, Long sessionId);

    /**
     * 根据最近一次复盘报告生成针对性补课学习计划。
     *
     * @param userId    当前用户 ID
     * @param sessionId 模拟面试会话 ID
     * @return 补课学习计划，包含薄弱知识点、推荐学习资源、优先级排序
     */
    MockInterviewStudyPlanVO buildStudyPlan(Long userId, Long sessionId);
}
