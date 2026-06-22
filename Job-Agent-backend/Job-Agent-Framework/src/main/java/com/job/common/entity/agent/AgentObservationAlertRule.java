package com.job.common.entity.agent;

import com.baomidou.mybatisplus.annotation.TableName;
import com.job.common.entity.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 作者: hfj
 * 功能: Agent 观测告警规则实体
 * 日期: 2026/6/22
 *
 * 说明:
 * 1. 告警规则只依赖 agent_observation_event，不反向影响 Agent 主流程。
 * 2. 第一版先保存站内告警记录，后续可以在规则上增加通知渠道字段。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("agent_observation_alert_rule")
public class AgentObservationAlertRule extends BaseEntity {

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

    private Date lastEvaluateTime;

    private Date lastAlertTime;

    private String remark;
}
