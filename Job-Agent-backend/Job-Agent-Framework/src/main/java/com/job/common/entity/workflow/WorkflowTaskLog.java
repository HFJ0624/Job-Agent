package com.job.common.entity.workflow;

import com.baomidou.mybatisplus.annotation.TableName;
import com.job.common.entity.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 工作流任务阶段日志实体。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("agent_workflow_task_log")
public class WorkflowTaskLog extends BaseEntity {

    private Long taskId;

    private String taskNo;

    private String taskType;

    private String stepName;

    private Integer progressPercent;

    private String logMessage;

    private String logLevel;

    private String errorMsg;
}
