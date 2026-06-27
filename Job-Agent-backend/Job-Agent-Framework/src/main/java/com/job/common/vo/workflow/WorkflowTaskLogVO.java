package com.job.common.vo.workflow;

import com.job.common.entity.workflow.WorkflowTaskLog;
import lombok.Builder;
import lombok.Data;

import java.util.Date;

/**
 * 工作流任务阶段日志展示对象。
 */
@Data
@Builder
public class WorkflowTaskLogVO {

    private Long id;

    private Long taskId;

    private String taskNo;

    private String taskType;

    private String stepName;

    private Integer progressPercent;

    private String logMessage;

    private String logLevel;

    private String errorMsg;

    private Date createTime;

    public static WorkflowTaskLogVO from(WorkflowTaskLog log) {
        if (log == null) {
            return null;
        }
        return WorkflowTaskLogVO.builder()
                .id(log.getId())
                .taskId(log.getTaskId())
                .taskNo(log.getTaskNo())
                .taskType(log.getTaskType())
                .stepName(log.getStepName())
                .progressPercent(log.getProgressPercent())
                .logMessage(log.getLogMessage())
                .logLevel(log.getLogLevel())
                .errorMsg(log.getErrorMsg())
                .createTime(log.getCreateTime())
                .build();
    }
}
