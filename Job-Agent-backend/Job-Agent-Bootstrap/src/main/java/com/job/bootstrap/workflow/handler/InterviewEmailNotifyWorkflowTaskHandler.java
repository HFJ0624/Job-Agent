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
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 功能：发送“已约面试”邮件通知的工作流处理器。
 *
 * 执行步骤：
 * 1. 根据任务 userId 查询用户邮箱。
 * 2. 根据任务 bizId 查询求职记录，重新读取数据库最新状态，避免使用过期快照。
 * 3. 拼接邮件标题和正文。
 * 4. 调用统一邮件服务发送邮件。
 * 5. 返回 JSON 结果，方便 admin 工作流页面查看执行结果。
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

        String subject = buildSubject(application);
        String content = buildContent(user, application);
        jobMailSenderService.sendText(user.getEmail(), subject, content);

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
