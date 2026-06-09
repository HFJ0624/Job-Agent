package com.job.common.entity.interview;

import com.baomidou.mybatisplus.annotation.TableName;
import com.job.common.entity.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 作者:hfj
 * 功能:模拟面试回答实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mock_interview_answer")
public class MockInterviewAnswer extends BaseEntity {

    /**
     * 模拟面试会话ID。
     */
    private Long sessionId;

    /**
     * 题目ID。
     */
    private Long questionId;

    /**
     * 用户ID。
     */
    private Long userId;

    /**
     * 用户回答内容。
     */
    private String answerContent;

    /**
     * 回答得分。
     */
    private BigDecimal score;

    /**
     * 回答等级。
     */
    private String level;

    /**
     * 回答优点。
     */
    private String strengths;

    /**
     * 存在问题。
     */
    private String problems;

    /**
     * 优化建议。
     */
    private String suggestions;
}