package com.job.common.dto.agent;

import lombok.Data;

/**
 * 作者: hfj
 * 功能: Agent 观测告警记录查询参数
 * 日期: 2026/6/22
 */
@Data
public class AgentObservationAlertRecordQueryDTO {

    private Long pageNum = 1L;

    private Long pageSize = 10L;

    private Long ruleId;

    private String ruleType;

    private String alertLevel;

    private String status;

    private String startTime;

    private String endTime;
}
