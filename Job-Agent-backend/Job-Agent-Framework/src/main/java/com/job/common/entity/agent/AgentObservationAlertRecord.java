package com.job.common.entity.agent;

import com.baomidou.mybatisplus.annotation.TableName;
import com.job.common.entity.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 作者: hfj
 * 功能: Agent 观测告警记录实体
 * 日期: 2026/6/22
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("agent_observation_alert_record")
public class AgentObservationAlertRecord extends BaseEntity {

    private Long ruleId;

    private String ruleName;

    private String ruleType;

    private String alertLevel;

    private BigDecimal metricValue;

    private BigDecimal thresholdValue;

    private Date windowStartTime;

    private Date windowEndTime;

    private String alertMessage;

    private String status;
}
