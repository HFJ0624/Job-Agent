package com.job.common.dto.workflow;

import lombok.Data;

/**
 * 创建工作流任务请求。
 */
@Data
public class WorkflowTaskCreateDTO {

    private String taskType;

    private Long bizId;

    private Long userId;

    private String requestJson;

    private Integer maxRetryCount;

    private Integer retryIntervalSeconds;
}
