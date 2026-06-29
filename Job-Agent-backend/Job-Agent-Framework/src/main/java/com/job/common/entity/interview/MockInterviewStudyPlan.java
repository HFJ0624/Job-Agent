package com.job.common.entity.interview;

import com.baomidou.mybatisplus.annotation.TableName;
import com.job.common.entity.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 模拟面试学习计划主表实体。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mock_interview_study_plan")
public class MockInterviewStudyPlan extends BaseEntity {

    /**
     * 用户ID。
     */
    private Long userId;

    /**
     * 计划标题。
     */
    private String planTitle;

    /**
     * 计划天数。
     */
    private Integer planDays;

    /**
     * 计划来源：WRONG_QUESTION / REVIEW。
     */
    private String source;

    /**
     * 本次计划覆盖的薄弱知识点，多行文本。
     */
    private String weakKnowledgePoints;

    /**
     * 计划状态：ACTIVE / FINISHED。
     */
    private String status;
}
