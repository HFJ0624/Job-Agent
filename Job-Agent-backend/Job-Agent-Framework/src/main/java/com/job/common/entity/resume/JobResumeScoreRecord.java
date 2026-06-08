package com.job.common.entity.resume;

import com.baomidou.mybatisplus.annotation.TableName;
import com.job.common.entity.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
/**
 * 作者:hfj
 * 功能:简历评分记录实体类，对应 resume_score_record 表
 * 日期:2026/6/6
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("resume_score_record")
public class JobResumeScoreRecord extends BaseEntity{

    /**
     * 用户ID。
     * 作用：保证用户只能查看和生成自己的简历评分。
     */
    private Long userId;

    /**
     * 简历ID。
     * 作用：一份简历可以产生多次评分记录，方便后续做历史对比。
     */
    private Long resumeId;

    /**
     * 简历总分，满分100。
     */
    private BigDecimal totalScore;

    /**
     * 基础信息分，满分10。
     */
    private BigDecimal basicInfoScore;

    /**
     * 教育背景分，满分10。
     */
    private BigDecimal educationScore;

    /**
     * 技能栈分，满分20。
     */
    private BigDecimal skillScore;

    /**
     * 项目经历分，满分35。
     */
    private BigDecimal projectScore;

    /**
     * 实习/工作经历分，满分15。
     */
    private BigDecimal experienceScore;

    /**
     * 表达质量分，满分10。
     */
    private BigDecimal expressionScore;

    /**
     * 用户本次评分时填写的目标岗位。
     * 第一版可以为空，后续可用于“面向Java后端岗位的简历评分”。
     */
    private String targetPosition;

    /**
     * 简历优势，多个优势按换行保存。
     */
    private String advantage;

    /**
     * 简历问题，多个问题按换行保存。
     */
    private String problem;

    /**
     * 优化建议，多个建议按换行保存。
     */
    private String suggestion;

    /**
     * 完整评分JSON。
     * 后续接入LLM后，可以把模型原始输出、评分细节、命中关键词都放进来。
     */
    private String scoreJson;
}
