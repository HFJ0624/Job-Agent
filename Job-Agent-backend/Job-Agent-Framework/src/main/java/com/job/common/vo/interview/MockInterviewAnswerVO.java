package com.job.common.vo.interview;

import com.job.common.entity.interview.MockInterviewAnswer;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 作者:hfj
 * 功能:模拟面试回答评分 VO
 */
@Data
public class MockInterviewAnswerVO {

    private Long id;
    private Long sessionId;
    private Long questionId;

    private String answerContent;
    private BigDecimal score;
    private String level;

    private List<String> strengths;
    private List<String> problems;
    private List<String> suggestions;

    /**
     * Entity 转 VO。
     */
    public static MockInterviewAnswerVO from(MockInterviewAnswer entity) {
        if (entity == null) {
            return null;
        }

        MockInterviewAnswerVO vo = new MockInterviewAnswerVO();
        vo.setId(entity.getId());
        vo.setSessionId(entity.getSessionId());
        vo.setQuestionId(entity.getQuestionId());
        vo.setAnswerContent(entity.getAnswerContent());
        vo.setScore(entity.getScore());
        vo.setLevel(entity.getLevel());
        vo.setStrengths(splitLines(entity.getStrengths()));
        vo.setProblems(splitLines(entity.getProblems()));
        vo.setSuggestions(splitLines(entity.getSuggestions()));
        return vo;
    }

    /**
     * 多行文本转列表。
     */
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
