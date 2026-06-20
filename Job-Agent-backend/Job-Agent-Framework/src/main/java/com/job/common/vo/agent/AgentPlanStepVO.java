package com.job.common.vo.agent;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.job.common.entity.agent.AgentPlanStep;
import lombok.Data;

import java.util.Date;

/**
 * 作者:hfj
 * 功能:Agent 计划步骤展示 VO
 * 日期:2026/6/19
 */
@Data
public class AgentPlanStepVO {

    private Long id;

    private Long planId;

    private Long userId;

    private Long conversationId;

    private Integer stepNo;

    private String stepName;

    private String stepGoal;

    private String toolName;

    private String toolInputSchema;

    private String completionCriteria;

    private String status;

    private String resultSummary;

    private String errorMsg;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updateTime;

    public static AgentPlanStepVO from(AgentPlanStep step) {
        if (step == null) {
            return null;
        }

        AgentPlanStepVO vo = new AgentPlanStepVO();
        vo.setId(step.getId());
        vo.setPlanId(step.getPlanId());
        vo.setUserId(step.getUserId());
        vo.setConversationId(step.getConversationId());
        vo.setStepNo(step.getStepNo());
        vo.setStepName(step.getStepName());
        vo.setStepGoal(step.getStepGoal());
        vo.setToolName(step.getToolName());
        vo.setToolInputSchema(step.getToolInputSchema());
        vo.setCompletionCriteria(step.getCompletionCriteria());
        vo.setStatus(step.getStatus());
        vo.setResultSummary(step.getResultSummary());
        vo.setErrorMsg(step.getErrorMsg());
        vo.setCreateTime(step.getCreateTime());
        vo.setUpdateTime(step.getUpdateTime());
        return vo;
    }
}
