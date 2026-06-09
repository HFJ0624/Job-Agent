package com.job.common.entity.interview;

import com.baomidou.mybatisplus.annotation.TableName;
import com.job.common.entity.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 作者:hfj
 * 功能:模拟面试会话实体
 *
 * 说明:
 * 1. 一次模拟面试对应一个 Session。
 * 2. Session 记录面试状态、当前答题进度、总分和总结。
 * 3. 每个 Session 下会有多道题和多条回答。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mock_interview_session")
public class MockInterviewSession extends BaseEntity {

    /**
     * 当前登录用户ID。
     */
    private Long userId;

    /**
     * 求职记录ID。
     */
    private Long applicationId;

    /**
     * 面试准备记录ID。
     */
    private Long interviewPrepareId;

    /**
     * 岗位ID。
     */
    private Long jobId;

    /**
     * 简历ID。
     */
    private Long resumeId;

    /**
     * 岗位名称快照。
     */
    private String jobTitle;

    /**
     * 公司名称快照。
     */
    private String companyName;

    /**
     * 会话状态：IN_PROGRESS / FINISHED。
     */
    private String status;

    /**
     * 当前答题进度。
     */
    private Integer currentIndex;

    /**
     * 总题数。
     */
    private Integer totalQuestionCount;

    /**
     * 本轮模拟面试总分。
     */
    private BigDecimal totalScore;

    /**
     * 本轮模拟面试总结。
     */
    private String summary;
}
