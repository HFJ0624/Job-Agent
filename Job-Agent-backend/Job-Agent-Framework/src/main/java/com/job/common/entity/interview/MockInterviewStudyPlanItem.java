package com.job.common.entity.interview;

import com.baomidou.mybatisplus.annotation.TableName;
import com.job.common.entity.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 模拟面试学习计划每日任务实体。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mock_interview_study_plan_item")
public class MockInterviewStudyPlanItem extends BaseEntity {

    /**
     * 学习计划ID。
     */
    private Long planId;

    /**
     * 用户ID。
     */
    private Long userId;

    /**
     * 第几天，从 1 开始。
     */
    private Integer dayNo;

    /**
     * 当天学习标题。
     */
    private String title;

    /**
     * 主要知识点。
     */
    private String knowledgePoint;

    /**
     * 学习目标。
     */
    private String learningGoal;

    /**
     * 练习任务。
     */
    private String practiceTask;

    /**
     * 复习建议。
     */
    private String reviewSuggestion;

    /**
     * RAG 学习材料 JSON。
     */
    private String materialsJson;

    /**
     * 完成状态：PENDING / DONE。
     */
    private String completionStatus;
}
