package com.job.bootstrap.service;

import com.job.common.dto.interview.MockInterviewAnswerDTO;
import com.job.common.dto.interview.MockInterviewStartDTO;
import com.job.common.vo.interview.MockInterviewAnswerVO;
import com.job.common.vo.interview.MockInterviewQuestionVO;
import com.job.common.vo.interview.MockInterviewSessionVO;

/**
 * 作者:hfj
 * 功能:模拟面试服务
 */
public interface MockInterviewService {

    /**
     * 开始一轮模拟面试。
     */
    MockInterviewSessionVO startSession(Long userId, MockInterviewStartDTO dto);

    /**
     * 查询模拟面试详情。
     */
    MockInterviewSessionVO getSessionDetail(Long userId, Long sessionId);

    /**
     * 查询当前应回答的题目。
     */
    MockInterviewQuestionVO getCurrentQuestion(Long userId, Long sessionId);

    /**
     * 提交回答并评分。
     */
    MockInterviewAnswerVO submitAnswer(Long userId, Long sessionId, MockInterviewAnswerDTO dto);

    /**
     * 手动结束模拟面试。
     */
    MockInterviewSessionVO finishSession(Long userId, Long sessionId);
}
