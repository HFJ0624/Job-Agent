package com.job.common.vo.interview;

import com.job.common.entity.interview.MockInterviewStudyPlanRetest;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 学习计划复测记录 VO。
 */
@Data
public class MockInterviewStudyPlanRetestVO {

    private Long id;
    private Long planId;
    private Long itemId;
    private String knowledgePoint;
    private String questionContent;
    private String standardAnswer;
    private String userAnswer;
    private BigDecimal score;
    private Boolean passed;
    private String feedback;
    private String suggestion;
    private String status;
    private String createTime;

    public static MockInterviewStudyPlanRetestVO from(MockInterviewStudyPlanRetest entity) {
        if (entity == null) {
            return null;
        }

        MockInterviewStudyPlanRetestVO vo = new MockInterviewStudyPlanRetestVO();
        vo.setId(entity.getId());
        vo.setPlanId(entity.getPlanId());
        vo.setItemId(entity.getItemId());
        vo.setKnowledgePoint(entity.getKnowledgePoint());
        vo.setQuestionContent(entity.getQuestionContent());
        vo.setStandardAnswer(entity.getStandardAnswer());
        vo.setUserAnswer(entity.getUserAnswer());
        vo.setScore(entity.getScore());
        vo.setPassed(entity.getPassedFlag() != null && entity.getPassedFlag() == 1);
        vo.setFeedback(entity.getFeedback());
        vo.setSuggestion(entity.getSuggestion());
        vo.setStatus(entity.getStatus());
        vo.setCreateTime(entity.getCreateTime() == null ? null : entity.getCreateTime().toString());
        return vo;
    }
}
