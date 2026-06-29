package com.job.bootstrap.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.job.bootstrap.mapper.JobReminderMapper;
import com.job.bootstrap.mapper.WorkflowTaskMapper;
import com.job.bootstrap.service.ApplicationFollowUpAgentService;
import com.job.bootstrap.service.InterviewPrepareService;
import com.job.bootstrap.service.WorkflowTaskService;
import com.job.common.dto.workflow.WorkflowTaskCreateDTO;
import com.job.common.entity.application.JobApplicationRecord;
import com.job.common.entity.reminder.JobReminder;
import com.job.common.entity.workflow.WorkflowTask;
import com.job.enums.ReminderStatus;
import com.job.enums.ReminderType;
import com.job.enums.WorkflowTaskStatus;
import com.job.enums.WorkflowTaskType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Calendar;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 功能：求职跟进 Agent 第一版实现。
 *
 * 设计说明：
 * 1. 这里不直接发送邮件，而是创建工作流任务，让异步调度器负责发送和重试。
 * 2. 面试准备提醒复用 job_reminder，避免新增一套重复的待办表。
 * 3. 面试准备材料复用 InterviewPrepareService，保证和用户手动点击“生成面试准备”得到的是同一套结果。
 */
@Service
@RequiredArgsConstructor
public class ApplicationFollowUpAgentServiceImpl implements ApplicationFollowUpAgentService {

    private static final int NOT_DELETED = 0;
    private static final int UNREAD = 0;
    private static final int INTERVIEW_ADVANCE_MINUTES = 30;

    private final JobReminderMapper jobReminderMapper;
    private final WorkflowTaskMapper workflowTaskMapper;
    private final WorkflowTaskService workflowTaskService;
    private final InterviewPrepareService interviewPrepareService;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void onInterviewScheduled(JobApplicationRecord application) {
        if (application == null || application.getId() == null || application.getInterviewTime() == null) {
            return;
        }

        /*
         * 1. 自动生成或更新面试准备提醒，让用户端提醒中心能看到这件事。
         */
        createOrUpdateInterviewPrepareReminder(application);

        /*
         * 2. 尝试提前生成面试准备材料。
         *    这里是“尽力而为”：准备材料失败不能阻断主流程，否则用户更新面试状态会被无关失败回滚。
         */
        tryGenerateInterviewPrepare(application);

        /*
         * 3. 创建异步邮件通知任务，发送失败由工作流重试，不影响当前求职状态更新。
         */
        createInterviewEmailTask(application);
    }

    private void createOrUpdateInterviewPrepareReminder(JobApplicationRecord application) {
        JobReminder reminder = jobReminderMapper.selectOne(
                new LambdaQueryWrapper<JobReminder>()
                        .eq(JobReminder::getUserId, application.getUserId())
                        .eq(JobReminder::getApplicationId, application.getId())
                        .eq(JobReminder::getReminderType, ReminderType.INTERVIEW.name())
                        .eq(JobReminder::getIsDeleted, NOT_DELETED)
                        .last("LIMIT 1")
        );

        if (reminder == null) {
            reminder = new JobReminder();
            reminder.setUserId(application.getUserId());
            reminder.setApplicationId(application.getId());
            reminder.setResumeId(application.getResumeId());
            reminder.setJobId(application.getJobId());
            reminder.setReminderType(ReminderType.INTERVIEW.name());
            reminder.setIsDeleted(NOT_DELETED);
        }

        Date eventTime = application.getInterviewTime();
        Date remindTime = minusMinutes(eventTime, INTERVIEW_ADVANCE_MINUTES);
        if (remindTime.before(new Date())) {
            remindTime = new Date();
        }

        reminder.setReminderTitle("面试准备提醒");
        reminder.setReminderContent(buildReminderContent(application));
        reminder.setEventTime(eventTime);
        reminder.setRemindTime(remindTime);
        reminder.setAdvanceMinutes(INTERVIEW_ADVANCE_MINUTES);
        reminder.setReminderStatus(ReminderStatus.PENDING.name());
        reminder.setIsRead(UNREAD);

        if (reminder.getId() == null) {
            jobReminderMapper.insert(reminder);
        } else {
            jobReminderMapper.updateById(reminder);
        }
    }

    private void createInterviewEmailTask(JobApplicationRecord application) {
        if (hasActiveInterviewEmailTask(application)) {
            return;
        }

        WorkflowTaskCreateDTO request = new WorkflowTaskCreateDTO();
        request.setTaskType(WorkflowTaskType.INTERVIEW_EMAIL_NOTIFY.name());
        request.setBizId(application.getId());
        request.setUserId(application.getUserId());
        request.setRequestJson(toJson(buildEmailTaskPayload(application)));
        request.setMaxRetryCount(3);
        request.setRetryIntervalSeconds(300);
        workflowTaskService.createTask(request);
    }

    private void tryGenerateInterviewPrepare(JobApplicationRecord application) {
        try {
            if (interviewPrepareService.getLatestPrepare(application.getUserId(), application.getId()) != null) {
                return;
            }
            interviewPrepareService.generatePrepare(
                    application.getUserId(),
                    application.getId(),
                    application.getResumeId()
            );
        } catch (Exception ignored) {
            /*
             * 面试准备材料属于增强能力，不应该影响求职状态、提醒和邮件任务创建。
             * 失败原因可以通过后续面试准备入口再次触发时暴露给用户。
             */
        }
    }

    private boolean hasActiveInterviewEmailTask(JobApplicationRecord application) {
        Long count = workflowTaskMapper.selectCount(
                new LambdaQueryWrapper<WorkflowTask>()
                        .eq(WorkflowTask::getTaskType, WorkflowTaskType.INTERVIEW_EMAIL_NOTIFY.name())
                        .eq(WorkflowTask::getBizId, application.getId())
                        .eq(WorkflowTask::getUserId, application.getUserId())
                        .in(WorkflowTask::getStatus,
                                WorkflowTaskStatus.PENDING.name(),
                                WorkflowTaskStatus.RUNNING.name(),
                                WorkflowTaskStatus.FAILED_RETRYABLE.name(),
                                WorkflowTaskStatus.SUCCESS.name())
                        .eq(WorkflowTask::getIsDeleted, NOT_DELETED)
        );
        return count != null && count > 0;
    }

    private Map<String, Object> buildEmailTaskPayload(JobApplicationRecord application) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("applicationId", application.getId());
        payload.put("jobId", application.getJobId());
        payload.put("resumeId", application.getResumeId());
        payload.put("companyName", application.getCompanyName());
        payload.put("jobTitle", application.getJobTitle());
        payload.put("interviewTime", application.getInterviewTime());
        return payload;
    }

    private String buildReminderContent(JobApplicationRecord application) {
        StringBuilder builder = new StringBuilder();
        builder.append("你已约面试，请提前准备简历项目、岗位 JD 和常见问答。");
        if (StringUtils.hasText(application.getCompanyName()) || StringUtils.hasText(application.getJobTitle())) {
            builder.append("目标：")
                    .append(safe(application.getCompanyName()))
                    .append(" ")
                    .append(safe(application.getJobTitle()))
                    .append("。");
        }
        return builder.toString();
    }

    private Date minusMinutes(Date date, int minutes) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        calendar.add(Calendar.MINUTE, -minutes);
        return calendar.getTime();
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            return "{}";
        }
    }

    private String safe(String value) {
        return StringUtils.hasText(value) ? value.trim() : "";
    }
}
