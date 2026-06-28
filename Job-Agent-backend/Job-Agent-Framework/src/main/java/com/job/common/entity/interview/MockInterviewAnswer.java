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

    /**
     * 是否基本答对，1 是，0 否。
     */
    private Integer correctFlag;

    /**
     * 用户回答与标准答案的语义相似度，0-100。
     */
    private BigDecimal similarityScore;

    /**
     * 命中的标准答案要点，多行文本存储。
     */
    private String matchedPoints;

    /**
     * 缺失的标准答案要点，多行文本存储。
     */
    private String missingPoints;

    /**
     * 本题暴露出的薄弱知识点，多行文本存储。
     */
    private String knowledgePoints;

    /**
     * 单题复盘结论，用于页面解释本题得分原因。
     */
    private String reviewConclusion;

    /**
     * 是否已进入错题本，1 是，0 否。
     */
    private Integer wrongBookFlag;
}
