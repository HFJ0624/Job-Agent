package com.job.common.entity.interview;

import com.baomidou.mybatisplus.annotation.TableName;
import com.job.common.entity.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 学习计划复测记录实体。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mock_interview_study_plan_retest")
public class MockInterviewStudyPlanRetest extends BaseEntity {

    /**
     * 用户ID。
     */
    private Long userId;

    /**
     * 学习计划ID。
     */
    private Long planId;

    /**
     * 学习计划每日任务ID。
     */
    private Long itemId;

    /**
     * 复测知识点。
     */
    private String knowledgePoint;

    /**
     * 复测题目。
     */
    private String questionContent;

    /**
     * 参考答案。
     */
    private String standardAnswer;

    /**
     * 用户回答。
     */
    private String userAnswer;

    /**
     * 复测得分。
     */
    private BigDecimal score;

    /**
     * 是否通过，1 是，0 否。
     */
    private Integer passedFlag;

    /**
     * AI 或规则给出的复测反馈。
     */
    private String feedback;

    /**
     * 下一步建议。
     */
    private String suggestion;

    /**
     * 状态：PENDING / SUBMITTED。
     */
    private String status;
}
