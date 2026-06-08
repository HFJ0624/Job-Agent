package com.job.common.entity.match;

import com.baomidou.mybatisplus.annotation.TableName;
import com.job.common.entity.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
/**
 * 作者:hfj
 * 功能:岗位匹配记录实体类
 * 说明:
 * 1. 一次“简历 + 岗位”的匹配分析会生成一条记录。
 * 2. 后续可以基于该表做历史匹配记录、岗位推荐排序和 Agent 工具调用追踪。
 * 日期: 2026/6/8 10:56
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("job_match_record")
public class JobMatchRecord extends BaseEntity {

    /**
     * 当前登录用户ID。
     */
    private Long userId;

    /**
     * 被用于匹配的简历ID。
     */
    private Long resumeId;

    /**
     * 被分析的岗位ID。
     */
    private Long jobId;

    /**
     * 最终匹配分，满分100。
     */
    private BigDecimal matchScore;

    /**
     * 规则匹配分，第一版主要使用规则分。
     */
    private BigDecimal ruleScore;

    /**
     * 技能匹配分。
     */
    private BigDecimal skillScore;

    /**
     * 项目经验匹配分。
     */
    private BigDecimal projectScore;

    /**
     * 学历、经验、城市等基础条件匹配分。
     */
    private BigDecimal conditionScore;

    /**
     * 求职偏好匹配分。
     * 第一版可以先给默认值，后面接 user_job_preference 表。
     */
    private BigDecimal preferenceScore;

    /**
     * 匹配等级，例如：高度匹配、较匹配、一般匹配、不匹配。
     */
    private String matchLevel;

    /**
     * 是否建议投递。
     */
    private Integer recommendApply;

    /**
     * 已匹配技能，JSON 数组字符串。
     */
    private String matchedSkills;

    /**
     * 缺失技能，JSON 数组字符串。
     */
    private String missingSkills;

    /**
     * 优势说明，多条内容用换行保存。
     */
    private String advantage;

    /**
     * 风险点，多条内容用换行保存。
     */
    private String riskPoints;

    /**
     * 优化建议，多条内容用换行保存。
     */
    private String suggestion;

    /**
     * 完整评分明细JSON，方便后续扩展 LLM 评分。
     */
    private String scoreJson;
}
