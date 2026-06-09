package com.job.bootstrap.service;

import com.job.common.vo.interview.MockInterviewReviewVO;

/**
 * 作者:hfj
 * 功能:模拟面试复盘报告服务
 */
public interface MockInterviewReviewService {

    /**
     * 生成模拟面试复盘报告。
     *
     * @param userId 当前用户ID
     * @param sessionId 模拟面试会话ID
     * @return 复盘报告
     */
    MockInterviewReviewVO generateReview(Long userId, Long sessionId);

    /**
     * 查询某轮模拟面试最近一次复盘报告。
     *
     * @param userId 当前用户ID
     * @param sessionId 模拟面试会话ID
     * @return 最近一次复盘报告
     */
    MockInterviewReviewVO getLatestReview(Long userId, Long sessionId);
}
