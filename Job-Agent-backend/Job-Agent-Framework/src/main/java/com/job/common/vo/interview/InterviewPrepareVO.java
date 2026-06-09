package com.job.common.vo.interview;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.job.common.entity.interview.InterviewPrepareRecord;
import lombok.Data;

import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * 作者:hfj
 * 功能:面试准备返回 VO
 */
@Data
public class InterviewPrepareVO {

    private Long id;
    private Long applicationId;
    private Long jobId;
    private Long resumeId;

    private String jobTitle;
    private String companyName;

    private List<String> technicalQuestions;
    private List<String> projectQuestions;
    private List<String> hrQuestions;
    private List<String> reviewSuggestions;

    private String summary;
    private String source;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    /**
     * Entity 转 VO。
     */
    public static InterviewPrepareVO from(InterviewPrepareRecord record, ObjectMapper objectMapper) {
        if (record == null) {
            return null;
        }

        InterviewPrepareVO vo = new InterviewPrepareVO();
        vo.setId(record.getId());
        vo.setApplicationId(record.getApplicationId());
        vo.setJobId(record.getJobId());
        vo.setResumeId(record.getResumeId());
        vo.setJobTitle(record.getJobTitle());
        vo.setCompanyName(record.getCompanyName());

        vo.setTechnicalQuestions(readList(record.getTechnicalQuestions(), objectMapper));
        vo.setProjectQuestions(readList(record.getProjectQuestions(), objectMapper));
        vo.setHrQuestions(readList(record.getHrQuestions(), objectMapper));
        vo.setReviewSuggestions(readList(record.getReviewSuggestions(), objectMapper));

        vo.setSummary(record.getSummary());
        vo.setSource(record.getSource());
        vo.setCreateTime(record.getCreateTime());
        return vo;
    }

    /**
     * JSON 字符串转 List。
     */
    private static List<String> readList(String json, ObjectMapper objectMapper) {
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
