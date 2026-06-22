package com.job.common.dto.agent;

import lombok.Data;

/**
 * 作者: hfj
 * 功能: Agent 观测告警规则查询参数
 * 日期: 2026/6/22
 */
@Data
public class AgentObservationAlertRuleQueryDTO {

    private Long pageNum = 1L;

    private Long pageSize = 10L;

    private String ruleName;

    private String ruleType;

    private String status;
}
