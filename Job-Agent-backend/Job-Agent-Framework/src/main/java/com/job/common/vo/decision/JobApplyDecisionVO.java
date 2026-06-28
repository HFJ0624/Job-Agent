package com.job.common.vo.decision;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.job.common.entity.decision.JobApplyDecisionRecord;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * 功能: AI 投递决策展示 VO。
 */
@Data
public class JobApplyDecisionVO {

    private Long id;

    private Long resumeId;

    private Long jobId;

    private String jobTitle;

    private String companyName;

    private String decision;

    private String decisionLabel;

    private BigDecimal decisionScore;

    private String reason;

    private List<String> risks;

    private List<String> resumeSuggestions;

    private List<String> interviewSuggestions;

    private List<String> nextActions;

    private Long matchRecordId;

    private String source;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    public static JobApplyDecisionVO from(JobApplyDecisionRecord record, ObjectMapper objectMapper) {
        if (record == null) {
            return null;
        }
        JobApplyDecisionVO vo = new JobApplyDecisionVO();
        vo.setId(record.getId());
        vo.setResumeId(record.getResumeId());
        vo.setJobId(record.getJobId());
        vo.setJobTitle(record.getJobTitle());
        vo.setCompanyName(record.getCompanyName());
        vo.setDecision(record.getDecision());
        vo.setDecisionLabel(record.getDecisionLabel());
        vo.setDecisionScore(record.getDecisionScore());
        vo.setReason(record.getReason());
        vo.setRisks(readStringList(record.getRisksJson(), objectMapper));
        vo.setResumeSuggestions(readStringList(record.getResumeSuggestionsJson(), objectMapper));
        vo.setInterviewSuggestions(readStringList(record.getInterviewSuggestionsJson(), objectMapper));
        vo.setNextActions(readStringList(record.getNextActionsJson(), objectMapper));
        vo.setMatchRecordId(record.getMatchRecordId());
        vo.setSource(record.getSource());
        vo.setCreateTime(record.getCreateTime());
        return vo;
    }

    private static List<String> readStringList(String json, ObjectMapper objectMapper) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception exception) {
            return Collections.emptyList();
        }
    }
}
