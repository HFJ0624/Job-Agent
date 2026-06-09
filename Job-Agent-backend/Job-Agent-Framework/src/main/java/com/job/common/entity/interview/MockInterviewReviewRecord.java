package com.job.common.entity.interview;

import com.baomidou.mybatisplus.annotation.TableName;
import com.job.common.entity.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 作者:hfj
 * 功能:模拟面试复盘报告实体
 * 说明:
 * 1. 一轮模拟面试结束后，可以生成一份复盘报告。
 * 2. 报告会总结本轮表现、薄弱题、能力短板和后续提升计划。
 * 3. 第一版使用规则生成，后续可以接入 LLM 生成更自然的复盘内容。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mock_interview_review_record")
public class MockInterviewReviewRecord extends BaseEntity {

    /**
     * 当前登录用户ID。
     */
    private Long userId;

    /**
     * 模拟面试会话ID。
     */
    private Long sessionId;

    /**
     * 求职记录ID。
     */
    private Long applicationId;

    /**
     * 岗位ID。
     */
    private Long jobId;

    /**
     * 岗位名称快照。
     */
    private String jobTitle;

    /**
     * 公司名称快照。
     */
    private String companyName;

    /**
     * 本轮模拟面试总分。
     */
    private BigDecimal totalScore;

    /**
     * 复盘等级，例如 优秀、良好、一般、待提升。
     */
    private String reviewLevel;

    /**
     * 已回答题目数量。
     */
    private Integer answeredCount;

    /**
     * 优势总结。
     */
    private String strengthSummary;

    /**
     * 短板总结。
     */
    private String weaknessSummary;

    /**
     * 提升计划。
     */
    private String improvementPlan;

    /**
     * 薄弱题目 JSON。
     */
    private String weakQuestions;

    /**
     * 能力标签 JSON。
     */
    private String abilityTags;

    /**
     * 分数明细 JSON。
     */
    private String scoreDetailJson;

    /**
     * 生成来源：RULE / LLM。
     */
    private String source;
}
