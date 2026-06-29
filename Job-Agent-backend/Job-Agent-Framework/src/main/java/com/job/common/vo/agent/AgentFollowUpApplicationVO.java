package com.job.common.vo.agent;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.job.common.entity.application.JobApplicationRecord;
import lombok.Data;

import java.util.Date;

/**
 * 求职跟进 Agent 明细展示对象。
 */
@Data
public class AgentFollowUpApplicationVO {

    private Long id;

    private Long userId;

    private Long jobId;

    private Long resumeId;

    private String companyName;

    private String jobTitle;

    private String status;

    private String priority;

    private String hrName;

    private String hrContact;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date applyTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date interviewTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date nextFollowTime;

    private String lastAction;

    private Long reminderCount;

    private Long pendingReminderCount;

    private Long emailTaskCount;

    private Long failedEmailTaskCount;

    private String latestEmailTaskStatus;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date latestEmailTaskTime;

    public static AgentFollowUpApplicationVO from(JobApplicationRecord entity) {
        if (entity == null) {
            return null;
        }
        AgentFollowUpApplicationVO vo = new AgentFollowUpApplicationVO();
        vo.setId(entity.getId());
        vo.setUserId(entity.getUserId());
        vo.setJobId(entity.getJobId());
        vo.setResumeId(entity.getResumeId());
        vo.setCompanyName(entity.getCompanyName());
        vo.setJobTitle(entity.getJobTitle());
        vo.setStatus(entity.getStatus());
        vo.setPriority(entity.getPriority());
        vo.setHrName(entity.getHrName());
        vo.setHrContact(entity.getHrContact());
        vo.setApplyTime(entity.getApplyTime());
        vo.setInterviewTime(entity.getInterviewTime());
        vo.setNextFollowTime(entity.getNextFollowTime());
        vo.setLastAction(entity.getLastAction());
        return vo;
    }
}
