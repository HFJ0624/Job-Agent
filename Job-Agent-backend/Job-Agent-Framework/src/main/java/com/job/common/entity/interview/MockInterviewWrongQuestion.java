package com.job.common.entity.interview;

import com.baomidou.mybatisplus.annotation.TableName;
import com.job.common.entity.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 模拟面试错题本实体。
 *
 * 说明:
 * 1. 每个用户、每道面试题保留一条错题记录，重复答错时更新次数和最新表现。
 * 2. 错题本用于后续补题计划、薄弱知识点统计和下一轮优先训练。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mock_interview_wrong_question")
public class MockInterviewWrongQuestion extends BaseEntity {

    /**
     * 用户ID。
     */
    private Long userId;

    /**
     * 最近一次答错所属面试会话ID。
     */
    private Long sessionId;

    /**
     * 最近一次答错的问题ID。
     */
    private Long questionId;

    /**
     * 最近一次答错的答案ID。
     */
    private Long answerId;

    /**
     * 岗位ID，便于按岗位方向筛选错题。
     */
    private Long jobId;

    /**
     * 简历ID，便于分析某份简历对应的薄弱项。
     */
    private Long resumeId;

    /**
     * 题目类型：TECHNICAL / PROJECT / HR。
     */
    private String questionType;

    /**
     * 题目内容快照。
     */
    private String questionContent;

    /**
     * 标准答案快照。
     */
    private String standardAnswer;

    /**
     * 用户最近一次回答内容。
     */
    private String lastAnswerContent;

    /**
     * 最近一次得分。
     */
    private BigDecimal lastScore;

    /**
     * 最近一次语义相似度。
     */
    private BigDecimal similarityScore;

    /**
     * 薄弱知识点，多行文本存储。
     */
    private String knowledgePoints;

    /**
     * 缺失要点，多行文本存储。
     */
    private String missingPoints;

    /**
     * 改进建议，多行文本存储。
     */
    private String suggestions;

    /**
     * 进入错题本的原因。
     */
    private String wrongReason;

    /**
     * 错误次数。
     */
    private Integer wrongCount;

    /**
     * 掌握状态：UNMASTERED / REVIEWING / MASTERED。
     */
    private String masteryStatus;
}
