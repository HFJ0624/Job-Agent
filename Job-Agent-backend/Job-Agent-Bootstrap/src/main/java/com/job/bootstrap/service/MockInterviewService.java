package com.job.bootstrap.service;

import com.job.common.dto.interview.AiInterviewStartDTO;
import com.job.common.dto.interview.MockInterviewAnswerDTO;
import com.job.common.dto.interview.MockInterviewStartDTO;
import com.job.common.vo.interview.MockInterviewAnswerVO;
import com.job.common.vo.interview.MockInterviewQuestionVO;
import com.job.common.vo.interview.MockInterviewSessionVO;
import org.springframework.web.multipart.MultipartFile;

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
     * 按简历和岗位直接开始 AI 语音面试。
     */
    MockInterviewSessionVO startAiInterview(Long userId, AiInterviewStartDTO dto);

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
     * 上传某道题的语音回答，识别成文字后复用文本评分链路。
     */
    MockInterviewAnswerVO submitAudioAnswer(Long userId, Long sessionId, Long questionId, MultipartFile audio, Integer durationSeconds);

    /**
     * 手动结束模拟面试。
     */
    MockInterviewSessionVO finishSession(Long userId, Long sessionId);
}
