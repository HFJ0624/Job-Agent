package com.job.common.entity.workflow;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.job.common.entity.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

/**
 * 工作流任务实体，对应 agent_workflow_task 表。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("agent_workflow_task")
public class WorkflowTask extends BaseEntity {

    private String taskNo;

    private String taskType;

    private Long bizId;

    private Long userId;

    private String requestJson;

    private String resultJson;

    private String status;

    private Integer progressPercent;

    private String currentStep;

    private Integer retryCount;

    private Integer maxRetryCount;

    private Integer retryIntervalSeconds;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date nextRunTime;

    private String lockedBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date lockTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date startTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date finishTime;

    private Long costTime;

    private String errorMsg;
}
