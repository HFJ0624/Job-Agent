package com.job.ai.agent.port;

import com.job.common.vo.interview.InterviewPrepareVO;

/** 功能: Agent 面试准备端口（Infra-Ai 定义，Bootstrap 实现）。 */
public interface InterviewPreparePort {

    InterviewPrepareVO generatePrepare(Long userId, Long applicationId, Long resumeId);
}
