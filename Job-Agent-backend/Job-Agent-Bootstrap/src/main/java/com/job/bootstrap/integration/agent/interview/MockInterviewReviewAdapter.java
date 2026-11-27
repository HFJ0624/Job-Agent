package com.job.bootstrap.integration.agent.interview;

import com.job.agent.port.MockInterviewReviewPort;
import com.job.bootstrap.service.MockInterviewReviewService;
import com.job.common.vo.interview.MockInterviewReviewVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 功能: Agent 模拟面试复盘端口的宿主实现，委托现有 MockInterviewReviewService。 */
@Component
@RequiredArgsConstructor
public class MockInterviewReviewAdapter implements MockInterviewReviewPort {

    private final MockInterviewReviewService mockInterviewReviewService;

    @Override
    public MockInterviewReviewVO generateReview(Long userId, Long sessionId) {
        return mockInterviewReviewService.generateReview(userId, sessionId);
    }
}
