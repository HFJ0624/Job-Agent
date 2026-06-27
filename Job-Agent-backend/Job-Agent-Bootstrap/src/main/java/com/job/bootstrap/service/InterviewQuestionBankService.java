package com.job.bootstrap.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.job.common.dto.interview.InterviewQuestionBankQueryDTO;
import com.job.common.dto.interview.InterviewQuestionImportDTO;
import com.job.common.vo.interview.InterviewQuestionBankVO;
import com.job.common.vo.interview.InterviewQuestionImportResultVO;
import com.job.common.vo.rag.RagIndexResultVO;

/**
 * AI 模拟面试题库后台服务。
 */
public interface InterviewQuestionBankService {

    InterviewQuestionImportResultVO importLocalMarkdown(InterviewQuestionImportDTO dto);

    IPage<InterviewQuestionBankVO> pageQuestions(InterviewQuestionBankQueryDTO query);

    InterviewQuestionBankVO getDetail(Long id);

    void updateStatus(Long id, String status);

    RagIndexResultVO indexQuestion(Long id);

    RagIndexResultVO indexAllActive();
}
