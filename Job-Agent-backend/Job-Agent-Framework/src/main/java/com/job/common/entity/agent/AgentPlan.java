package com.job.common.entity.agent;

import com.baomidou.mybatisplus.annotation.TableName;
import com.job.common.entity.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 作者:hfj
 * 功能:Agent 计划实体
 * 日期:2026/6/19
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("agent_plan")
public class AgentPlan extends BaseEntity {

    /**
     * 本轮对话 TraceId。
     */
    private String traceId;

    /**
     * 用户ID。
     */
    private Long userId;

    /**
     * 会话ID。
     */
    private Long conversationId;

    /**
     * 意图编码。
     */
    private String intentCode;

    /**
     * 用户原始目标。
     */
    private String userGoal;

    /**
     * 计划标题。
     */
    private String planTitle;

    /**
     * 计划摘要。
     */
    private String planSummary;

    /**
     * 必要参数 JSON。
     */
    private String requiredParamsJson;

    /**
     * 已抽取参数 JSON。
     */
    private String extractedParamsJson;

    /**
     * 缺失参数 JSON。
     */
    private String missingParamsJson;

    /**
     * 计划状态。
     */
    private String status;

    /**
     * 失败原因。
     */
    private String failReason;

    /**
     * 失败后重规划次数（任务 G2 Replan）。
     * 步骤失败且还有预算时，执行器会让 Replanner 给出替代步骤；该字段用于有界控制与审计。
     */
    private Integer replanCount;
}
