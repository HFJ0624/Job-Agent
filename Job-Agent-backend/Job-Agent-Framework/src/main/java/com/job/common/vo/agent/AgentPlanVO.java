package com.job.common.vo.agent;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.job.common.entity.agent.AgentPlan;
import lombok.Data;

import java.util.Date;
import java.util.List;

/**
 * 作者:hfj
 * 功能:Agent 计划展示 VO
 * 日期:2026/6/19
 */
@Data
public class AgentPlanVO {

    private Long id;

    private String traceId;

    private Long userId;

    private Long conversationId;

    private String intentCode;

    private String userGoal;

    private String planTitle;

    private String planSummary;

    private String requiredParamsJson;

    private String extractedParamsJson;

    private String missingParamsJson;

    private String status;

    private String failReason;

    private List<AgentPlanStepVO> steps = List.of();

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updateTime;

    public static AgentPlanVO from(AgentPlan plan) {
        if (plan == null) {
            return null;
        }

        AgentPlanVO vo = new AgentPlanVO();
        vo.setId(plan.getId());
        vo.setTraceId(plan.getTraceId());
        vo.setUserId(plan.getUserId());
        vo.setConversationId(plan.getConversationId());
        vo.setIntentCode(plan.getIntentCode());
        vo.setUserGoal(plan.getUserGoal());
        vo.setPlanTitle(plan.getPlanTitle());
        vo.setPlanSummary(plan.getPlanSummary());
        vo.setRequiredParamsJson(plan.getRequiredParamsJson());
        vo.setExtractedParamsJson(plan.getExtractedParamsJson());
        vo.setMissingParamsJson(plan.getMissingParamsJson());
        vo.setStatus(plan.getStatus());
        vo.setFailReason(plan.getFailReason());
        vo.setCreateTime(plan.getCreateTime());
        vo.setUpdateTime(plan.getUpdateTime());
        return vo;
    }
}
