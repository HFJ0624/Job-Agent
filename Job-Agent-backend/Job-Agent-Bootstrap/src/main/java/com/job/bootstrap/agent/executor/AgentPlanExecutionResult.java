package com.job.bootstrap.agent.executor;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 作者:hfj
 * 功能:Agent 计划执行结果
 * 日期:2026/6/20
 */
@Data
@Builder
public class AgentPlanExecutionResult {

    /**
     * 计划ID。
     */
    private Long planId;

    /**
     * 计划是否整体成功。
     */
    private Boolean success;

    /**
     * 计划最终状态。
     */
    private String status;

    /**
     * 面向用户或后台的执行摘要。
     */
    private String message;

    /**
     * 各步骤执行结果。
     */
    private List<AgentPlanStepExecutionResult> steps;
}
