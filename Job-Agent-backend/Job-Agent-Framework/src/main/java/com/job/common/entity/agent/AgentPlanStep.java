package com.job.common.entity.agent;

import com.baomidou.mybatisplus.annotation.TableName;
import com.job.common.entity.base.BaseEntity;
import lombok.Data;
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
     * 结果摘要。
     */
    private String resultSummary;

    /**
     * 错误信息。
     */
    private String errorMsg;
}
