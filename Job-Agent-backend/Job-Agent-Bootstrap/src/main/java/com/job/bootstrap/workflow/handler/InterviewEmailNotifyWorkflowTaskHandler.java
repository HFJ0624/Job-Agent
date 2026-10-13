package com.job.bootstrap.workflow.handler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.job.bootstrap.mapper.JobApplicationRecordMapper;
import com.job.bootstrap.mapper.JobUserMapper;
import com.job.bootstrap.service.JobMailSenderService;
import com.job.bootstrap.service.WorkflowTaskProgressService;
import com.job.bootstrap.workflow.WorkflowTaskHandler;
import com.job.common.entity.application.JobApplicationRecord;
import com.job.common.entity.user.JobUser;
import com.job.common.entity.workflow.WorkflowTask;
import com.job.enums.WorkflowTaskType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 功能：发送“已约面试”邮件通知的工作流处理器。
 *
 * 执行步骤：
 * 1. 根据任务 userId 查询用户邮箱。
 * 2. 根据任务 bizId 查询求职记录，重新读取数据库最新状态，避免使用过期快照。
 * 3. 幂等抢占：通过求职记录上的 interview_email_sent_at 状态位做原子条件更新，
 *    只有抢占成功（影响 1 行）的本次执行才真正发送，确保同一求职记录只发一封邮件。
 * 4. 拼接邮件标题和正文，调用统一邮件服务发送邮件。
 * 5. 返回 JSON 结果，方便 admin 工作流页面查看执行结果。
 *
 * 幂等设计说明：
 * - 任务被 markFailure 置为 FAILED_RETRYABLE 后会重新执行同一任务行，
 *   若 handler 无幂等，会重复发信；这里用业务状态位 interview_email_sent_at 兜底。
 * - 采用“先抢占后发送”，发送失败时回滚状态位（clearInterviewEmailNotify），
 *   让下次重试能重新发送，避免“标记了却没发出去”的漏发。
 */
@Component
@RequiredArgsConstructor
public class InterviewEmailNotifyWorkflowTaskHandler implements WorkflowTaskHandler {

    private static final int NOT_DELETED = 0;
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm");

    private final JobUserMapper jobUserMapper;
    private final JobApplicationRecordMapper jobApplicationRecordMapper;
    private final JobMailSenderService jobMailSenderService;
    private final WorkflowTaskProgressService workflowTaskProgressService;
    private final ObjectMapper objectMapper;

    @Override
    public String taskType() {
        return WorkflowTaskType.INTERVIEW_EMAIL_NOTIFY.name();
    }

    @Override
    public String handle(WorkflowTask task) {
        if (task.getUserId() == null || task.getBizId() == null) {
            throw new IllegalArgumentException("面试邮件通知任务缺少 userId 或 applicationId");
        }

        workflowTaskProgressService.recordProgress(
                task.getId(),
                "查询通知数据",
                20,
                "开始查询用户邮箱和求职记录",
                "INFO",
                null
        );

        JobUser user = jobUserMapper.selectById(task.getUserId());
        if (user == null || !StringUtils.hasText(user.getEmail())) {
            throw new IllegalArgumentException("用户未配置邮箱，无法发送面试通知");
        }

        JobApplicationRecord application = jobApplicationRecordMapper.selectOne(
                new LambdaQueryWrapper<JobApplicationRecord>()
                        .eq(JobApplicationRecord::getId, task.getBizId())
                        .eq(JobApplicationRecord::getUserId, task.getUserId())
                        .eq(JobApplicationRecord::getIsDeleted, NOT_DELETED)
                        .last("LIMIT 1")
        );
        if (application == null) {
            throw new IllegalArgumentException("求职记录不存在，无法发送面试通知");
        }

        workflowTaskProgressService.recordProgress(
                task.getId(),
                "发送邮件",
                60,
                "开始发送已约面试通知邮件",
                "INFO",
                user.getEmail()
        );

        // 幂等抢占：把 interview_email_sent_at 从 NULL 原子置为当前时间，
        // 只有更新成功（影响 1 行）的本次执行才拿到发送权，其余直接跳过。
        int claimed = jobApplicationRecordMapper.claimInterviewEmailNotify(task.getBizId(), new Date());
        if (claimed <= 0) {
            workflowTaskProgressService.recordProgress(
                    task.getId(),
                    "跳过发送",
                    100,
                    "该求职记录已发送过面试通知邮件，跳过重复发送",
                    "INFO",
                    null
            );
            Map<String, Object> skipped = new LinkedHashMap<>();
            skipped.put("to", user.getEmail());
            skipped.put("applicationId", application.getId());
            skipped.put("sent", false);
            skipped.put("skipped", true);
            skipped.put("reason", "already_notified");
            return toJson(skipped);
        }

        String subject = buildSubject(application);
        String content = buildContent(user, application);
        try {
            jobMailSenderService.sendText(user.getEmail(), subject, content);
        } catch (Exception exception) {
            // 发送失败：回滚幂等状态位，允许下次重试重新发送，避免漏发。
            jobApplicationRecordMapper.clearInterviewEmailNotify(task.getBizId(), new Date());
            throw exception;
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("to", user.getEmail());
        result.put("applicationId", application.getId());
        result.put("companyName", application.getCompanyName());
        result.put("jobTitle", application.getJobTitle());
        result.put("sent", true);
        return toJson(result);
    }

    private String buildSubject(JobApplicationRecord application) {
        return "已约面试提醒：" + safe(application.getCompanyName(), "目标公司")
                + " - " + safe(application.getJobTitle(), "目标岗位");
    }

    private String buildContent(JobUser user, JobApplicationRecord application) {
        String nickname = StringUtils.hasText(user.getNickname()) ? user.getNickname() : user.getUsername();
        String interviewTime = application.getInterviewTime() == null
                ? "待确认"
                : DATE_FORMAT.format(application.getInterviewTime());

        return """
                %s，你好：

                你的求职进度已更新为“已约面试”。

                公司：%s
                岗位：%s
                面试时间：%s

                系统已为你创建面试准备提醒，并尝试生成面试准备材料。建议你提前复盘简历项目、岗位 JD、技术关键词和常见 HR 问题。

                祝你面试顺利。
                """.formatted(
                safe(nickname, "同学"),
                safe(application.getCompanyName(), "目标公司"),
                safe(application.getJobTitle(), "目标岗位"),
                interviewTime
        );
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            return "{}";
        }
    }

    private String safe(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value.trim() : defaultValue;
    }
}
