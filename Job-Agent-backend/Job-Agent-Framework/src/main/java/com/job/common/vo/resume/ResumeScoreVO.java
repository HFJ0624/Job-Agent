package com.job.common.vo.resume;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.job.common.entity.resume.JobResumeScoreRecord;
import lombok.Data;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * 作者:hfj
 * 功能:简历评分结果 VO，返回给前端展示
 * 日期:2026/6/15
 *
 * 设计说明:
 * 1. 保留 V1 字段，兼容旧前端和历史评分记录。
 * 2. 新增 V2 字段，承载“总分 + 八维分 + 维度解释 + 风险点 + 总结”的完整评分结构。
 * 3. V2 完整结果优先从 score_json 解析；如果是历史记录，则自动退回旧字段展示。
 */
@Data
public class ResumeScoreVO {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

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

    /**
     * V2 评分版本。
     * 说明: 新评分为 V2，历史记录没有 score_json 时会回退为 V1。
     */
    private String scoreVersion;

    /**
     * V2 总分，范围 0-100。
     * 说明: 为了兼容旧字段，totalScore 仍然保留；前端可优先展示 overallScore。
     */
    private Integer overallScore;

    /**
     * V2 八个维度的分数明细。
     */
    private ScoreBreakdown scoreBreakdown;

    /**
     * V2 每个维度的原因、问题和建议。
     */
    private List<ScoreDimension> dimensions;

    /**
     * V2 简历优势。
     */
    private List<String> strengths;

    /**
     * V2 简历不足。
     */
    private List<String> weaknesses;

    /**
     * V2 风险点，例如技能缺少项目证据、缺少量化成果等。
     */
    private List<String> riskPoints;

    /**
     * V2 可执行优化建议。
     */
    private List<String> improvementSuggestions;

    /**
     * V2 总结性评价。
     */
    private String summary;

    /**
     * LLM 辅助点评状态: SUCCESS / FAILED / SKIPPED。
     */
    private String llmStatus;

    /**
     * LLM 调用失败时的简短原因。
     */
    private String llmError;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    /**
     * Entity 转 VO。
     *
     * @param record 数据库评分记录
     * @return 前端展示 VO
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

        // 先放一份旧字段兜底值，score_json 解析成功时再覆盖为 V2 结构。
        fillLegacyFields(vo);
        fillV2Fields(vo, record.getScoreJson());
        return vo;
    }

    /**
     * 用旧字段构造兜底展示数据。
     */
    private static void fillLegacyFields(ResumeScoreVO vo) {
        vo.setScoreVersion("V1");
        vo.setOverallScore(toInteger(vo.getTotalScore()));
        vo.setScoreBreakdown(ScoreBreakdown.fromLegacy(vo));
        vo.setDimensions(ScoreDimension.fromLegacy(vo));
        vo.setStrengths(vo.getAdvantages());
        vo.setWeaknesses(vo.getProblems());
        vo.setRiskPoints(Collections.emptyList());
        vo.setImprovementSuggestions(vo.getSuggestions());
        vo.setSummary("");
        vo.setLlmStatus("SKIPPED");
        vo.setLlmError(null);
    }

    /**
     * 从 score_json 解析 V2 完整结果。
     * 说明: 解析失败不抛异常，直接保留旧字段兜底，避免历史脏数据影响页面展示。
     */
    private static void fillV2Fields(ResumeScoreVO vo, String scoreJson) {
        if (scoreJson == null || scoreJson.isBlank()) {
            return;
        }

        try {
            JsonNode root = OBJECT_MAPPER.readTree(scoreJson);
            if (root == null || !root.isObject()) {
                return;
            }

            vo.setScoreVersion(textValue(root, "scoreVersion", vo.getScoreVersion()));
            vo.setOverallScore(intValue(root, "overallScore", vo.getOverallScore()));
            vo.setLevel(textValue(root, "level", vo.getLevel()));
            vo.setScoreBreakdown(readBreakdown(root.path("scoreBreakdown"), vo.getScoreBreakdown()));
            vo.setDimensions(readDimensions(root.path("dimensions"), vo.getDimensions()));
            vo.setStrengths(readStringArray(root.path("strengths"), vo.getStrengths()));
            vo.setWeaknesses(readStringArray(root.path("weaknesses"), vo.getWeaknesses()));
            vo.setRiskPoints(readStringArray(root.path("riskPoints"), vo.getRiskPoints()));
            vo.setImprovementSuggestions(readStringArray(root.path("improvementSuggestions"), vo.getImprovementSuggestions()));
            vo.setSummary(textValue(root, "summary", vo.getSummary()));
            vo.setLlmStatus(textValue(root, "llmStatus", vo.getLlmStatus()));
            vo.setLlmError(textValue(root, "llmError", vo.getLlmError()));
        } catch (Exception ignored) {
            // score_json 是展示增强字段，解析失败时不影响主评分记录返回。
        }
    }

    private static ScoreBreakdown readBreakdown(JsonNode node, ScoreBreakdown fallback) {
        if (node == null || !node.isObject()) {
            return fallback;
        }

        ScoreBreakdown breakdown = new ScoreBreakdown();
        breakdown.setBasicInfoScore(intValue(node, "basicInfoScore", fallback.getBasicInfoScore()));
        breakdown.setCareerGoalScore(intValue(node, "careerGoalScore", fallback.getCareerGoalScore()));
        breakdown.setEducationScore(intValue(node, "educationScore", fallback.getEducationScore()));
        breakdown.setSkillsScore(intValue(node, "skillsScore", fallback.getSkillsScore()));
        breakdown.setProjectExperienceScore(intValue(node, "projectExperienceScore", fallback.getProjectExperienceScore()));
        breakdown.setWorkExperienceScore(intValue(node, "workExperienceScore", fallback.getWorkExperienceScore()));
        breakdown.setQuantifiedImpactScore(intValue(node, "quantifiedImpactScore", fallback.getQuantifiedImpactScore()));
        breakdown.setFormatScore(intValue(node, "formatScore", fallback.getFormatScore()));
        return breakdown;
    }

    private static List<ScoreDimension> readDimensions(JsonNode node, List<ScoreDimension> fallback) {
        if (node == null || !node.isArray()) {
            return fallback;
        }

        List<ScoreDimension> dimensions = new ArrayList<>();
        for (JsonNode item : node) {
            ScoreDimension dimension = new ScoreDimension();
            dimension.setDimensionName(textValue(item, "dimensionName", ""));
            dimension.setScore(intValue(item, "score", 0));
            dimension.setMaxScore(intValue(item, "maxScore", 0));
            dimension.setReason(textValue(item, "reason", ""));
            dimension.setIssues(readStringArray(item.path("issues"), Collections.emptyList()));
            dimension.setSuggestions(readStringArray(item.path("suggestions"), Collections.emptyList()));

            if (dimension.getDimensionName() != null && !dimension.getDimensionName().isBlank()) {
                dimensions.add(dimension);
            }
        }

        return dimensions.isEmpty() ? fallback : dimensions;
    }

    private static List<String> readStringArray(JsonNode node, List<String> fallback) {
        if (node == null || !node.isArray()) {
            return fallback == null ? Collections.emptyList() : fallback;
        }

        List<String> values = new ArrayList<>();
        for (JsonNode item : node) {
            if (item.isTextual() && !item.asText().isBlank()) {
                values.add(item.asText().trim());
            }
        }
        return values.isEmpty() ? (fallback == null ? Collections.emptyList() : fallback) : values;
    }

    private static String textValue(JsonNode node, String fieldName, String fallback) {
        JsonNode value = node.path(fieldName);
        if (value.isMissingNode() || value.isNull()) {
            return fallback;
        }
        String text = value.asText();
        return text == null || text.isBlank() ? fallback : text;
    }

    private static Integer intValue(JsonNode node, String fieldName, Integer fallback) {
        JsonNode value = node.path(fieldName);
        if (value.isNumber()) {
            return value.asInt();
        }
        if (value.isTextual()) {
            try {
                return Integer.parseInt(value.asText());
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private static Integer toInteger(BigDecimal value) {
        if (value == null) {
            return 0;
        }
        return value.setScale(0, RoundingMode.HALF_UP).intValue();
    }

    /**
     * 根据总分生成 V2 等级。
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
            return "一般";
        }
        if (value >= 60) {
            return "较弱";
        }
        return "需要重写";
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

    /**
     * V2 八个维度分数。
     */
    @Data
    public static class ScoreBreakdown {
        private Integer basicInfoScore;
        private Integer careerGoalScore;
        private Integer educationScore;
        private Integer skillsScore;
        private Integer projectExperienceScore;
        private Integer workExperienceScore;
        private Integer quantifiedImpactScore;
        private Integer formatScore;

        private static ScoreBreakdown fromLegacy(ResumeScoreVO vo) {
            ScoreBreakdown breakdown = new ScoreBreakdown();
            breakdown.setBasicInfoScore(toInteger(vo.getBasicInfoScore()));
            breakdown.setCareerGoalScore(0);
            breakdown.setEducationScore(toInteger(vo.getEducationScore()));
            breakdown.setSkillsScore(toInteger(vo.getSkillScore()));
            breakdown.setProjectExperienceScore(toInteger(vo.getProjectScore()));
            breakdown.setWorkExperienceScore(toInteger(vo.getExperienceScore()));
            breakdown.setQuantifiedImpactScore(0);
            breakdown.setFormatScore(toInteger(vo.getExpressionScore()));
            return breakdown;
        }
    }

    /**
     * V2 单个评分维度解释。
     */
    @Data
    public static class ScoreDimension {
        private String dimensionName;
        private Integer score;
        private Integer maxScore;
        private String reason;
        private List<String> issues;
        private List<String> suggestions;

        private static List<ScoreDimension> fromLegacy(ResumeScoreVO vo) {
            List<ScoreDimension> dimensions = new ArrayList<>();
            dimensions.add(legacyDimension("基础信息", toInteger(vo.getBasicInfoScore()), 10));
            dimensions.add(legacyDimension("教育背景", toInteger(vo.getEducationScore()), 10));
            dimensions.add(legacyDimension("技能栈", toInteger(vo.getSkillScore()), 20));
            dimensions.add(legacyDimension("项目经历", toInteger(vo.getProjectScore()), 35));
            dimensions.add(legacyDimension("工作经历", toInteger(vo.getExperienceScore()), 15));
            dimensions.add(legacyDimension("表达质量", toInteger(vo.getExpressionScore()), 10));
            return dimensions;
        }

        private static ScoreDimension legacyDimension(String name, Integer score, Integer maxScore) {
            ScoreDimension dimension = new ScoreDimension();
            dimension.setDimensionName(name);
            dimension.setScore(score);
            dimension.setMaxScore(maxScore);
            dimension.setReason("历史评分记录，未保存 V2 维度解释。");
            dimension.setIssues(Collections.emptyList());
            dimension.setSuggestions(Collections.emptyList());
            return dimension;
        }
    }
}
