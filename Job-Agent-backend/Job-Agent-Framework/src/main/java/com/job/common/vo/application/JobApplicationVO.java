package com.job.common.vo.application;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.job.common.entity.application.JobApplicationRecord;
import lombok.Data;

import java.util.Date;

/**
 * 作者:hfj
 * 功能:求职记录展示 VO
 */
@Data
public class JobApplicationVO {

    private Long id;
    private Long jobId;
    private Long resumeId;
    private Long companyId;

    private String companyName;
    private String jobTitle;
    private String city;
    private String salaryText;
    private String source;

    private String status;
    private String statusText;
    private String priority;
    private String priorityText;

    private String hrName;
    private String hrContact;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date applyTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date interviewTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date nextFollowTime;

    private String note;
    private String lastAction;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updateTime;

    /**
     * Entity 转 VO。
     *
     * @param entity 投递记录实体
     * @return 前端展示对象
     */
    public static JobApplicationVO from(JobApplicationRecord entity) {
        if (entity == null) {
            return null;
        }

        JobApplicationVO vo = new JobApplicationVO();
        vo.setId(entity.getId());
        vo.setJobId(entity.getJobId());
        vo.setResumeId(entity.getResumeId());
        vo.setCompanyId(entity.getCompanyId());

        vo.setCompanyName(entity.getCompanyName());
        vo.setJobTitle(entity.getJobTitle());
        vo.setCity(entity.getCity());
        vo.setSalaryText(entity.getSalaryText());
        vo.setSource(entity.getSource());

        vo.setStatus(entity.getStatus());
        vo.setStatusText(resolveStatusText(entity.getStatus()));
        vo.setPriority(entity.getPriority());
        vo.setPriorityText(resolvePriorityText(entity.getPriority()));

        vo.setHrName(entity.getHrName());
        vo.setHrContact(entity.getHrContact());
        vo.setApplyTime(entity.getApplyTime());
        vo.setInterviewTime(entity.getInterviewTime());
        vo.setNextFollowTime(entity.getNextFollowTime());
        vo.setNote(entity.getNote());
        vo.setLastAction(entity.getLastAction());
        vo.setCreateTime(entity.getCreateTime());
        vo.setUpdateTime(entity.getUpdateTime());
        return vo;
    }

    /**
     * 状态中文展示。
     */
    private static String resolveStatusText(String status) {
        if ("INTERESTED".equals(status)) {
            return "感兴趣";
        }
        if ("COMMUNICATED".equals(status)) {
            return "已沟通";
        }
        if ("APPLIED".equals(status)) {
            return "已投递";
        }
        if ("INTERVIEWING".equals(status)) {
            return "面试中";
        }
        if ("OFFER".equals(status)) {
            return "Offer";
        }
        if ("REJECTED".equals(status)) {
            return "已拒绝";
        }
        if ("CLOSED".equals(status)) {
            return "已结束";
        }
        return "未知状态";
    }

    /**
     * 优先级中文展示。
     */
    private static String resolvePriorityText(String priority) {
        if ("HIGH".equals(priority)) {
            return "高";
        }
        if ("LOW".equals(priority)) {
            return "低";
        }
        return "普通";
    }
}
