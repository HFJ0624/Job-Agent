package com.job.common.dto.agent;

import lombok.Data;

/**
 * 求职跟进 Agent 规则分页查询参数。
 */
@Data
public class AgentFollowUpRuleQueryDTO {

    private Long pageNum = 1L;

    private Long pageSize = 10L;

    private String ruleName;

    private String ruleType;

    private String status;
}
