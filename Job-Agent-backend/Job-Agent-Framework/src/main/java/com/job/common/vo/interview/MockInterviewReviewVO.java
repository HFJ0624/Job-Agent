package com.job.common.vo.interview;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.job.common.entity.interview.MockInterviewReviewRecord;
import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * 作者:hfj
 * 功能:模拟面试复盘报告 VO
 */
@Data
public class MockInterviewReviewVO {

    private Long id;
    private Long sessionId;
    private Long applicationId;
    private Long jobId;

    private String jobTitle;
    private String companyName;

    private BigDecimal totalScore;
    private String reviewLevel;
    private Integer answeredCount;

    private String strengthSummary;
    private String weaknessSummary;
    private String improvementPlan;

    private List<String> weakQuestions;
    private List<String> abilityTags;
    private List<QuestionReviewItem> questionReviews = new ArrayList<>();

    private String source;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    /**
     * Entity 转 VO。
     */
    public static MockInterviewReviewVO from(MockInterviewReviewRecord record, ObjectMapper objectMapper) {
        if (record == null) {
            return null;
        }

        MockInterviewReviewVO vo = new MockInterviewReviewVO();
        vo.setId(record.getId());
        vo.setSessionId(record.getSessionId());
        vo.setApplicationId(record.getApplicationId());
        vo.setJobId(record.getJobId());
        vo.setJobTitle(record.getJobTitle());
        vo.setCompanyName(record.getCompanyName());
        vo.setTotalScore(record.getTotalScore());
        vo.setReviewLevel(record.getReviewLevel());
        vo.setAnsweredCount(record.getAnsweredCount());
        vo.setStrengthSummary(record.getStrengthSummary());
        vo.setWeaknessSummary(record.getWeaknessSummary());
        vo.setImprovementPlan(record.getImprovementPlan());
        vo.setWeakQuestions(readStringList(record.getWeakQuestions(), objectMapper));
        vo.setAbilityTags(readStringList(record.getAbilityTags(), objectMapper));
        vo.setSource(record.getSource());
        vo.setCreateTime(record.getCreateTime());
        return vo;
    }

    /**
     * JSON 字符串转字符串列表。
     */
    private static List<String> readStringList(String json, ObjectMapper objectMapper) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }

        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    /**
     * 单题复盘明细。
     *
     * 说明:
     * 1. 总体复盘记录只保存一场面试的总体结论。
     * 2. 单题复盘事实来自 mock_interview_question 和 mock_interview_answer。
     * 3. 前端详情页直接使用该结构展示标准答案、用户答案、相似度、缺失点和错题本状态。
     */
    @Data
    public static class QuestionReviewItem {
        private Long questionId;
        private Long answerId;
        private Integer sortNo;
        private String questionType;
        private String questionContent;
        private String standardAnswer;
        private String userAnswer;
        private BigDecimal score;
        private String level;
        private Boolean correct;
        private BigDecimal similarityScore;
        private List<String> matchedPoints = Collections.emptyList();
        private List<String> missingPoints = Collections.emptyList();
        private List<String> knowledgePoints = Collections.emptyList();
        private String reviewConclusion;
        private List<String> strengths = Collections.emptyList();
        private List<String> problems = Collections.emptyList();
        private List<String> suggestions = Collections.emptyList();
        private Boolean wrongBook;
    }
}
