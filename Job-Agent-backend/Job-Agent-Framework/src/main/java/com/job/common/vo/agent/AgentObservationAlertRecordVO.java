package com.job.common.vo.agent;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.job.common.entity.agent.AgentObservationAlertRecord;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 作者: hfj
 * 功能: Agent 观测告警记录 VO
 * 日期: 2026/6/22
 */
@Data
public class AgentObservationAlertRecordVO {

    private Long id;

    private Long ruleId;

    private String ruleName;

    private String ruleType;

    private String alertLevel;

    private BigDecimal metricValue;

    private BigDecimal thresholdValue;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date windowStartTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date windowEndTime;

    private String alertMessage;

    private String status;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    public static AgentObservationAlertRecordVO from(AgentObservationAlertRecord entity) {
        if (entity == null) {
            return null;
        }

        AgentObservationAlertRecordVO vo = new AgentObservationAlertRecordVO();
        vo.setId(entity.getId());
        vo.setRuleId(entity.getRuleId());
        vo.setRuleName(entity.getRuleName());
        vo.setRuleType(entity.getRuleType());
        vo.setAlertLevel(entity.getAlertLevel());
        vo.setMetricValue(entity.getMetricValue());
        vo.setThresholdValue(entity.getThresholdValue());
        vo.setWindowStartTime(entity.getWindowStartTime());
        vo.setWindowEndTime(entity.getWindowEndTime());
        vo.setAlertMessage(entity.getAlertMessage());
        vo.setStatus(entity.getStatus());
        vo.setCreateTime(entity.getCreateTime());
        return vo;
    }
}
