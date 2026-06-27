package com.job.common.vo.workflow;

import com.job.common.entity.workflow.WorkflowTask;
import lombok.Builder;
import lombok.Data;

import java.util.Date;

/**
 * 工作流任务展示对象。
 */
@Data
@Builder
public class WorkflowTaskVO {

    private Long id;

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

    private Date nextRunTime;

    private String lockedBy;

    private Date lockTime;

    private Date startTime;

    private Date finishTime;

    private Long costTime;

    private String errorMsg;

    private Date createTime;

    private Date updateTime;

    public static WorkflowTaskVO from(WorkflowTask task) {
        if (task == null) {
            return null;
        }
        return WorkflowTaskVO.builder()
                .id(task.getId())
                .taskNo(task.getTaskNo())
                .taskType(task.getTaskType())
                .bizId(task.getBizId())
                .userId(task.getUserId())
                .requestJson(task.getRequestJson())
                .resultJson(task.getResultJson())
                .status(task.getStatus())
                .progressPercent(task.getProgressPercent())
                .currentStep(task.getCurrentStep())
                .retryCount(task.getRetryCount())
                .maxRetryCount(task.getMaxRetryCount())
                .retryIntervalSeconds(task.getRetryIntervalSeconds())
                .nextRunTime(task.getNextRunTime())
                .lockedBy(task.getLockedBy())
                .lockTime(task.getLockTime())
                .startTime(task.getStartTime())
                .finishTime(task.getFinishTime())
                .costTime(task.getCostTime())
                .errorMsg(task.getErrorMsg())
                .createTime(task.getCreateTime())
                .updateTime(task.getUpdateTime())
                .build();
    }
}
