package com.job.common.dto.workflow;

import lombok.Data;

/**
 * 工作流任务分页查询条件。
 */
@Data
public class WorkflowTaskQueryDTO {

    private Long pageNum = 1L;

    private Long pageSize = 10L;

    private String taskNo;

    private String taskType;

    private String status;

    private Long bizId;

    private Long userId;

    private String startTime;

    private String endTime;
}
