package com.job.common.vo.interview;

import com.job.common.entity.interview.MockInterviewWrongQuestion;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 模拟面试错题本 VO。
 */
@Data
public class MockInterviewWrongQuestionVO {

    private Long id;
    private Long sessionId;
    private Long questionId;
    private Long answerId;
    private Long jobId;
    private Long resumeId;
    private String questionType;
    private String questionContent;
    private String standardAnswer;
    private String lastAnswerContent;
    private BigDecimal lastScore;
    private BigDecimal similarityScore;
    private List<String> knowledgePoints;
    private List<String> missingPoints;
    private List<String> suggestions;
    private String wrongReason;
    private Integer wrongCount;
    private String masteryStatus;
    private String createTime;
    private String updateTime;

    public static MockInterviewWrongQuestionVO from(MockInterviewWrongQuestion entity) {
        if (entity == null) {
            return null;
        }

        MockInterviewWrongQuestionVO vo = new MockInterviewWrongQuestionVO();
        vo.setId(entity.getId());
        vo.setSessionId(entity.getSessionId());
        vo.setQuestionId(entity.getQuestionId());
        vo.setAnswerId(entity.getAnswerId());
        vo.setJobId(entity.getJobId());
        vo.setResumeId(entity.getResumeId());
        vo.setQuestionType(entity.getQuestionType());
        vo.setQuestionContent(entity.getQuestionContent());
        vo.setStandardAnswer(entity.getStandardAnswer());
        vo.setLastAnswerContent(entity.getLastAnswerContent());
        vo.setLastScore(entity.getLastScore());
        vo.setSimilarityScore(entity.getSimilarityScore());
        vo.setKnowledgePoints(splitLines(entity.getKnowledgePoints()));
        vo.setMissingPoints(splitLines(entity.getMissingPoints()));
        vo.setSuggestions(splitLines(entity.getSuggestions()));
        vo.setWrongReason(entity.getWrongReason());
        vo.setWrongCount(entity.getWrongCount());
        vo.setMasteryStatus(entity.getMasteryStatus());
        vo.setCreateTime(entity.getCreateTime() == null ? null : entity.getCreateTime().toString());
        vo.setUpdateTime(entity.getUpdateTime() == null ? null : entity.getUpdateTime().toString());
        return vo;
    }

    private static List<String> splitLines(String value) {
        if (value == null || value.isBlank()) {
            return Collections.emptyList();
        }

        return Arrays.stream(value.split("\\R+"))
                .map(String::trim)
                .filter(item -> !item.isEmpty())
                .toList();
    }
}
