package com.job.common.dto.agent;

import lombok.Data;

/**
 * 求职跟进 Agent 明细分页查询参数。
 */
@Data
public class AgentFollowUpApplicationQueryDTO {

    private Long pageNum = 1L;

    private Long pageSize = 10L;

    private Long userId;

    private String status;

    private String keyword;

    private Boolean failedEmailOnly;
}
