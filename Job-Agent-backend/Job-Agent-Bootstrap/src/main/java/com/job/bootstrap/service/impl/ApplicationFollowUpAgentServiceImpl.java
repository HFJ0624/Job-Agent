package com.job.bootstrap.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.job.bootstrap.mapper.AgentFollowUpRuleMapper;
import com.job.bootstrap.mapper.JobReminderMapper;
import com.job.bootstrap.mapper.WorkflowTaskMapper;
import com.job.bootstrap.service.ApplicationFollowUpAgentService;
import com.job.bootstrap.service.InterviewPrepareService;
import com.job.bootstrap.service.WorkflowTaskService;
import com.job.common.dto.workflow.WorkflowTaskCreateDTO;
import com.job.common.entity.agent.AgentFollowUpRule;
import com.job.common.entity.application.JobApplicationRecord;
import com.job.common.entity.reminder.JobReminder;
import com.job.common.entity.workflow.WorkflowTask;
import com.job.enums.AgentFollowUpRuleStatus;
import com.job.enums.AgentFollowUpRuleType;
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
 * 求职跟进 Agent 事件服务。
 */
@Service
@RequiredArgsConstructor
public class ApplicationFollowUpAgentServiceImpl implements ApplicationFollowUpAgentService {

    private static final int NOT_DELETED = 0;
    private static final int UNREAD = 0;
    private static final int DEFAULT_INTERVIEW_ADVANCE_MINUTES = 30;
    private static final int DEFAULT_MAX_RETRY_COUNT = 3;
    private static final int DEFAULT_RETRY_INTERVAL_SECONDS = 300;

    private final AgentFollowUpRuleMapper agentFollowUpRuleMapper;
    private final JobReminderMapper jobReminderMapper;
    private final WorkflowTaskMapper workflowTaskMapper;
    private final WorkflowTaskService workflowTaskService;
    private final InterviewPrepareService interviewPrepareService;
    private final ObjectMapper objectMapper;

    /**
     * 面试已确认事件入口。
     *
     * 步骤：
     * 1. 读取后台启用的 INTERVIEW_SCHEDULED 规则，让提醒时间、文案、邮件开关可配置。
     * 2. 创建或更新面试准备提醒，避免用户重复改面试时间后生成多条提醒。
     * 3. 尝试生成面试准备材料，失败不阻断主流程。
     * 4. 按规则创建异步邮件任务，让工作流负责发送、重试和失败记录。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void onInterviewScheduled(JobApplicationRecord application) {
        if (application == null || application.getId() == null || application.getInterviewTime() == null) {
            return;
        }
        AgentFollowUpRule rule = loadInterviewScheduledRule();
        createOrUpdateInterviewPrepareReminder(application, rule);
        tryGenerateInterviewPrepare(application);
        createInterviewEmailTask(application, rule);
    }

    private void createOrUpdateInterviewPrepareReminder(JobApplicationRecord application, AgentFollowUpRule rule) {
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
            reminder.setCreateTime(new Date());
        }

        int advanceMinutes = interviewAdvanceMinutes(rule);
        Date eventTime = application.getInterviewTime();
        Date remindTime = minusMinutes(eventTime, advanceMinutes);
        if (remindTime.before(new Date())) {
            remindTime = new Date();
        }

        reminder.setReminderTitle(safeText(rule == null ? null : rule.getReminderTitle(), "面试准备提醒"));
        reminder.setReminderContent(renderRuleTemplate(rule, application));
        reminder.setEventTime(eventTime);
        reminder.setRemindTime(remindTime);
        reminder.setAdvanceMinutes(advanceMinutes);
        reminder.setReminderStatus(ReminderStatus.PENDING.name());
        reminder.setIsRead(UNREAD);
        reminder.setUpdateTime(new Date());

        if (reminder.getId() == null) {
            jobReminderMapper.insert(reminder);
        } else {
            jobReminderMapper.updateById(reminder);
        }
    }

    private void createInterviewEmailTask(JobApplicationRecord application, AgentFollowUpRule rule) {
        if (rule != null && safeInt(rule.getEmailEnabled()) <= 0) {
            return;
        }
        if (hasActiveInterviewEmailTask(application)) {
            return;
        }

        WorkflowTaskCreateDTO request = new WorkflowTaskCreateDTO();
        request.setTaskType(WorkflowTaskType.INTERVIEW_EMAIL_NOTIFY.name());
        request.setBizId(application.getId());
        request.setUserId(application.getUserId());
        request.setRequestJson(toJson(buildEmailTaskPayload(application)));
        request.setMaxRetryCount(rule == null ? DEFAULT_MAX_RETRY_COUNT : safePositive(rule.getMaxRetryCount(), DEFAULT_MAX_RETRY_COUNT));
        request.setRetryIntervalSeconds(rule == null ? DEFAULT_RETRY_INTERVAL_SECONDS : safePositive(rule.getRetryIntervalSeconds(), DEFAULT_RETRY_INTERVAL_SECONDS));
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
             * 面试准备材料是增强能力，不能影响状态更新、提醒创建和邮件任务创建。
             * 真实失败原因可以在用户手动重新生成准备材料时暴露，主链路这里保持可用。
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

    private AgentFollowUpRule loadInterviewScheduledRule() {
        return agentFollowUpRuleMapper.selectOne(
                new LambdaQueryWrapper<AgentFollowUpRule>()
                        .eq(AgentFollowUpRule::getRuleType, AgentFollowUpRuleType.INTERVIEW_SCHEDULED.name())
                        .eq(AgentFollowUpRule::getStatus, AgentFollowUpRuleStatus.ENABLED.name())
                        .eq(AgentFollowUpRule::getIsDeleted, NOT_DELETED)
                        .orderByDesc(AgentFollowUpRule::getCreateTime)
                        .last("LIMIT 1")
        );
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

    private String renderRuleTemplate(AgentFollowUpRule rule, JobApplicationRecord application) {
        String template = rule == null ? null : rule.getReminderTemplate();
        if (!StringUtils.hasText(template)) {
            return buildDefaultReminderContent(application);
        }
        return template.trim()
                .replace("{companyName}", safe(application.getCompanyName()))
                .replace("{jobTitle}", safe(application.getJobTitle()))
                .replace("{hrName}", safe(application.getHrName()))
                .replace("{status}", safe(application.getStatus()));
    }

    private String buildDefaultReminderContent(JobApplicationRecord application) {
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

    private int interviewAdvanceMinutes(AgentFollowUpRule rule) {
        if (rule == null || rule.getDelayMinutes() == null || rule.getDelayMinutes() == 0) {
            return DEFAULT_INTERVIEW_ADVANCE_MINUTES;
        }
        return Math.abs(rule.getDelayMinutes());
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

    private String safeText(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value.trim() : defaultValue;
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private int safePositive(Integer value, int defaultValue) {
        return value == null || value <= 0 ? defaultValue : value;
    }
}
