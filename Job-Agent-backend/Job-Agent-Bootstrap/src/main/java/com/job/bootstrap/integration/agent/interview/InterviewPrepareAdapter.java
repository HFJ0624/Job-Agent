package com.job.bootstrap.integration.agent.interview;

import com.job.ai.agent.port.InterviewPreparePort;
import com.job.bootstrap.service.InterviewPrepareService;
import com.job.common.vo.interview.InterviewPrepareVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 功能: Agent 面试准备端口的宿主实现，委托现有 InterviewPrepareService。 */
@Component
@RequiredArgsConstructor
public class InterviewPrepareAdapter implements InterviewPreparePort {

    private final InterviewPrepareService interviewPrepareService;

    @Override
    public InterviewPrepareVO generatePrepare(Long userId, Long applicationId, Long resumeId) {
        return interviewPrepareService.generatePrepare(userId, applicationId, resumeId);
    }
}
