package com.job.common.entity.interview;

import com.baomidou.mybatisplus.annotation.TableName;
import com.job.common.entity.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 作者:hfj
 * 功能:模拟面试题目实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mock_interview_question")
public class MockInterviewQuestion extends BaseEntity {

    /**
     * 模拟面试会话ID。
     */
    private Long sessionId;

    /**
     * 用户ID。
     */
    private Long userId;

    /**
     * 题目类型：TECHNICAL / PROJECT / HR。
     */
    private String questionType;

    /**
     * 题目内容。
     */
    private String questionContent;

    /**
     * 题目顺序。
     */
    private Integer sortNo;

    /**
     * 是否已回答，0否，1是。
     */
    private Integer answered;
}
