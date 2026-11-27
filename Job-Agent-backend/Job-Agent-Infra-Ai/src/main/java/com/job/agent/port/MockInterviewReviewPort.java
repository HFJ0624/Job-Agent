package com.job.agent.port;

import com.job.common.vo.interview.MockInterviewReviewVO;

/** 功能: Agent 模拟面试复盘端口（Infra-Ai 定义，Bootstrap 实现）。 */
public interface MockInterviewReviewPort {

    MockInterviewReviewVO generateReview(Long userId, Long sessionId);
}
