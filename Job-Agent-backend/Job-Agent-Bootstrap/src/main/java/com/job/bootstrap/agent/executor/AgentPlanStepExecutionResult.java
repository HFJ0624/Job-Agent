package com.job.bootstrap.agent.executor;

import lombok.Builder;
import lombok.Data;

/**
 * 作者:hfj
 * 功能:Agent 计划步骤执行结果
 * 日期:2026/6/20
 */
@Data
@Builder
public class AgentPlanStepExecutionResult {

    /**
     * 步骤ID。
     */
    private Long stepId;

    /**
     * 步骤序号。
     */
    private Integer stepNo;

    /**
     * 步骤名称。
     */
    private String stepName;

    /**
     * 工具名称。
     */
    private String toolName;

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

    /**
     * 是否为幂等命中。
     *
     * true 表示该步骤本轮没有真实调用工具，直接采用了历史执行结果，
     * 用于失败恢复时跳过已完成的步骤，避免副作用工具重复执行。
     */
    private Boolean idempotentHit;

    /**
     * 工具执行结果。
     */
    private AgentToolExecutionResult toolResult;
}
