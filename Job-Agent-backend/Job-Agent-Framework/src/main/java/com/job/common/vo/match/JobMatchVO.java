package com.job.common.vo.match;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.job.common.entity.match.JobMatchRecord;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Date;
import java.util.List;
/**
 * 作者:hfj
 * 功能:岗位匹配结果VO，返回给前端展示
 * 日期: 2026/6/8 10:58
 */
@Data
public class JobMatchVO {

    private Long id;
    private Long userId;
    private Long resumeId;
    private Long jobId;

    private BigDecimal matchScore;
    private BigDecimal ruleScore;
    private BigDecimal skillScore;
    private BigDecimal projectScore;
    private BigDecimal conditionScore;
    private BigDecimal preferenceScore;

    private String matchLevel;
    private Boolean recommendApply;

    private List<String> matchedSkills;
    private List<String> missingSkills;
    private List<String> advantages;
    private List<String> riskPoints;
    private List<String> suggestions;

    /**
     * 匹配证据列表（任务 J5）：技能与基础条件的“要求 ↔ 简历原文”对，可逐条核对。
     */
    private List<MatchEvidenceVO> evidences;

    /**
     * 硬性条件违反项（任务 J5）：用户明确要求（城市/岗位/薪资/学历）与岗位不符的清单。
     * 空列表表示通过；非空时 recommendApply 一定为 false。
     */
    private List<String> hardConditionViolations;

    /**
     * 硬性条件是否通过（任务 J5）。无偏好数据时视为通过（不误伤）。
     */
    private Boolean hardConditionPassed;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    /**
     * Entity 转 VO。
     * 说明：数据库里 JSON 和多行文本不适合前端直接展示，所以这里统一转换。
     */
    public static JobMatchVO from(JobMatchRecord record, ObjectMapper objectMapper) {
        if (record == null) {
            return null;
        }

        JobMatchVO vo = new JobMatchVO();
        vo.setId(record.getId());
        vo.setUserId(record.getUserId());
        vo.setResumeId(record.getResumeId());
        vo.setJobId(record.getJobId());

        vo.setMatchScore(record.getMatchScore());
        vo.setRuleScore(record.getRuleScore());
        vo.setSkillScore(record.getSkillScore());
        vo.setProjectScore(record.getProjectScore());
        vo.setConditionScore(record.getConditionScore());
        vo.setPreferenceScore(record.getPreferenceScore());

        vo.setMatchLevel(record.getMatchLevel());
        vo.setRecommendApply(record.getRecommendApply() != null && record.getRecommendApply() == 1);

        vo.setMatchedSkills(readStringList(record.getMatchedSkills(), objectMapper));
        vo.setMissingSkills(readStringList(record.getMissingSkills(), objectMapper));
        vo.setAdvantages(splitLines(record.getAdvantage()));
        vo.setRiskPoints(splitLines(record.getRiskPoints()));
        vo.setSuggestions(splitLines(record.getSuggestion()));
        vo.setCreateTime(record.getCreateTime());
        return vo;
    }

    /**
     * JSON 数组字符串转 List。
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
     * 多行文本转 List。
     */
    private static List<String> splitLines(String text) {
        if (text == null || text.isBlank()) {
            return Collections.emptyList();
        }

        return List.of(text.split("\\R+"))
                .stream()
                .map(String::trim)
                .filter(item -> !item.isEmpty())
                .toList();
    }
}
