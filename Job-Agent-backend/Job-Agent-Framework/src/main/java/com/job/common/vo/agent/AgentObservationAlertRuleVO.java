package com.job.common.vo.agent;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.job.common.entity.agent.AgentObservationAlertRule;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 作者: hfj
 * 功能: Agent 观测告警规则 VO
 * 日期: 2026/6/22
 */
@Data
public class AgentObservationAlertRuleVO {

    private Long id;

    private String ruleName;

    private String ruleType;

    private String eventType;

    private String errorCategory;

    private String modelCode;

    private String toolName;

    private BigDecimal thresholdValue;

    private Integer windowMinutes;

    private Integer minSampleCount;

    private Integer cooldownMinutes;

    private String alertLevel;

    private String status;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date lastEvaluateTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date lastAlertTime;

    private String remark;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    public static AgentObservationAlertRuleVO from(AgentObservationAlertRule entity) {
        if (entity == null) {
            return null;
        }

        AgentObservationAlertRuleVO vo = new AgentObservationAlertRuleVO();
        vo.setId(entity.getId());
        vo.setRuleName(entity.getRuleName());
        vo.setRuleType(entity.getRuleType());
        vo.setEventType(entity.getEventType());
        vo.setErrorCategory(entity.getErrorCategory());
        vo.setModelCode(entity.getModelCode());
        vo.setToolName(entity.getToolName());
        vo.setThresholdValue(entity.getThresholdValue());
        vo.setWindowMinutes(entity.getWindowMinutes());
        vo.setMinSampleCount(entity.getMinSampleCount());
        vo.setCooldownMinutes(entity.getCooldownMinutes());
        vo.setAlertLevel(entity.getAlertLevel());
        vo.setStatus(entity.getStatus());
        vo.setLastEvaluateTime(entity.getLastEvaluateTime());
        vo.setLastAlertTime(entity.getLastAlertTime());
        vo.setRemark(entity.getRemark());
        vo.setCreateTime(entity.getCreateTime());
        return vo;
    }
}
