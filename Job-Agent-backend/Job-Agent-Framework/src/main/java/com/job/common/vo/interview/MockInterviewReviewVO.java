package com.job.common.vo.interview;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.job.common.entity.interview.MockInterviewReviewRecord;
import lombok.Data;

import java.math.BigDecimal;
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
}
