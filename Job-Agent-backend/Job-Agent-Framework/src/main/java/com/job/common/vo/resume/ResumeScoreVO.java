package com.job.common.vo.resume;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.job.common.entity.resume.JobResumeScoreRecord;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * 作者:hfj
 * 功能:简历评分结果VO，返回给前端展示
 * 日期:2026/6/6
 */
@Data
public class ResumeScoreVO {

    private Long id;
    private Long resumeId;
    private Long userId;

    private BigDecimal totalScore;
    private String level;

    private BigDecimal basicInfoScore;
    private BigDecimal educationScore;
    private BigDecimal skillScore;
    private BigDecimal projectScore;
    private BigDecimal experienceScore;
    private BigDecimal expressionScore;

    private String targetPosition;

    private List<String> advantages;
    private List<String> problems;
    private List<String> suggestions;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    /**
     * Entity 转 VO。
     *
     * @param record 数据库评分记录
     * @return 前端展示VO
     */
    public static ResumeScoreVO from(JobResumeScoreRecord record) {
        if (record == null) {
            return null;
        }

        ResumeScoreVO vo = new ResumeScoreVO();
        vo.setId(record.getId());
        vo.setResumeId(record.getResumeId());
        vo.setUserId(record.getUserId());
        vo.setTotalScore(record.getTotalScore());
        vo.setLevel(resolveLevel(record.getTotalScore()));

        vo.setBasicInfoScore(record.getBasicInfoScore());
        vo.setEducationScore(record.getEducationScore());
        vo.setSkillScore(record.getSkillScore());
        vo.setProjectScore(record.getProjectScore());
        vo.setExperienceScore(record.getExperienceScore());
        vo.setExpressionScore(record.getExpressionScore());

        vo.setTargetPosition(record.getTargetPosition());
        vo.setAdvantages(splitLines(record.getAdvantage()));
        vo.setProblems(splitLines(record.getProblem()));
        vo.setSuggestions(splitLines(record.getSuggestion()));
        vo.setCreateTime(record.getCreateTime());
        return vo;
    }

    /**
     * 根据总分生成等级。
     */
    private static String resolveLevel(BigDecimal score) {
        if (score == null) {
            return "未评分";
        }

        double value = score.doubleValue();
        if (value >= 90) {
            return "优秀";
        }
        if (value >= 80) {
            return "良好";
        }
        if (value >= 70) {
            return "中等";
        }
        if (value >= 60) {
            return "待优化";
        }
        return "问题较多";
    }

    /**
     * 将按行保存的文本转成数组，方便前端列表展示。
     */
    private static List<String> splitLines(String text) {
        if (text == null || text.isBlank()) {
            return Collections.emptyList();
        }

        return Arrays.stream(text.split("\\R+"))
                .map(String::trim)
                .filter(item -> !item.isEmpty())
                .toList();
    }
}
