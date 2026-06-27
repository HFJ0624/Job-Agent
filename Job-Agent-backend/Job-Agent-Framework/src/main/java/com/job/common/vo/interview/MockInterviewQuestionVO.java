package com.job.common.vo.interview;

import com.job.common.entity.interview.MockInterviewQuestion;
import lombok.Data;

/**
 * 作者:hfj
 * 功能:模拟面试题目 VO
 */
@Data
public class MockInterviewQuestionVO {

    private Long id;
    private Long sessionId;
    private String questionType;
    private Long questionBankId;
    private Long ragChunkId;
    private String questionContent;
    private String standardAnswer;
    private Integer sortNo;
    private Integer answered;

    /**
     * Entity 转 VO。
     */
    public static MockInterviewQuestionVO from(MockInterviewQuestion entity) {
        if (entity == null) {
            return null;
        }

        MockInterviewQuestionVO vo = new MockInterviewQuestionVO();
        vo.setId(entity.getId());
        vo.setSessionId(entity.getSessionId());
        vo.setQuestionType(entity.getQuestionType());
        vo.setQuestionBankId(entity.getQuestionBankId());
        vo.setRagChunkId(entity.getRagChunkId());
        vo.setQuestionContent(entity.getQuestionContent());
        vo.setStandardAnswer(entity.getStandardAnswer());
        vo.setSortNo(entity.getSortNo());
        vo.setAnswered(entity.getAnswered());
        return vo;
    }
}
