package com.job.bootstrap.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.job.common.dto.interview.MockInterviewWrongQuestionQueryDTO;
import com.job.common.dto.interview.MockInterviewWrongQuestionStatusDTO;
import com.job.common.vo.interview.MockInterviewWrongQuestionVO;

import java.util.List;

/**
 * 模拟面试错题本服务。
 */
public interface MockInterviewWrongQuestionService {

    /**
     * 分页查询当前用户错题。
     */
    IPage<MockInterviewWrongQuestionVO> pageWrongQuestions(Long userId, MockInterviewWrongQuestionQueryDTO query);

    /**
     * 修改错题掌握状态。
     */
    MockInterviewWrongQuestionVO updateMasteryStatus(Long userId, Long id, MockInterviewWrongQuestionStatusDTO dto);

    /**
     * 查询当前用户未掌握/复习中的薄弱知识点，用于下一轮面试优先抽题。
     */
    List<String> listActiveWeakKnowledgePoints(Long userId, int limit);
}
