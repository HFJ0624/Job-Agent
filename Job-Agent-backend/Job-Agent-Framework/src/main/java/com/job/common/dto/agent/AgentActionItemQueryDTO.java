package com.job.common.dto.agent;

import lombok.Data;

/**
 * Admin 行动项分页查询条件。
 */
@Data
public class AgentActionItemQueryDTO {

    private Long pageNum = 1L;

    private Long pageSize = 10L;

    private Long userId;

    private String sourceType;

    private String actionType;

    private String actionStatus;

    private Boolean failedOnly;

    private Boolean hasWorkflowTask;

    private Long workflowTaskId;

    private String keyword;
}
