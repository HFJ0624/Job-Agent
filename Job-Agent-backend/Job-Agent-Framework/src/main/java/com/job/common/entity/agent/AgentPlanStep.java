package com.job.common.entity.agent;

import com.baomidou.mybatisplus.annotation.TableName;
import com.job.common.entity.base.BaseEntity;
import lombok.Data;

import java.util.Date;
import lombok.EqualsAndHashCode;

/**
 * 作者:hfj
 * 功能:Agent 计划步骤实体
 * 日期:2026/6/19
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("agent_plan_step")
public class AgentPlanStep extends BaseEntity {

    /**
     * 计划ID。
     */
    private Long planId;

    /**
     * 用户ID。
     */
    private Long userId;

    /**
     * 会话ID。
     */
    private Long conversationId;

    /**
     * 步骤序号。
     */
    private Integer stepNo;

    /**
     * 步骤名称。
     */
    private String stepName;

    /**
     * 步骤目标。
     */
    private String stepGoal;

    /**
     * 建议工具名称。
     */
    private String toolName;

    /**
     * 工具入参说明 JSON。
     */
    private String toolInputSchema;

    /**
     * 完成条件。
     */
    private String completionCriteria;

    /**
     * 步骤状态。
     */
    private String status;

    /**
     * 尝试次数（任务 J2）：每次置 RUNNING +1，用于失败分布与恢复观测。
     */
    private Integer attemptCount;

    /**
     * 本次执行开始时间（任务 J2）。
     */
    private Date startedAt;

    /**
     * 本次执行结束时间（任务 J2，完成/失败时落库）。
     */
    private Date finishedAt;

    /**
     * 结果摘要。
     */
    private String resultSummary;

    /**
     * 错误信息。
     */
    private String errorMsg;
}
