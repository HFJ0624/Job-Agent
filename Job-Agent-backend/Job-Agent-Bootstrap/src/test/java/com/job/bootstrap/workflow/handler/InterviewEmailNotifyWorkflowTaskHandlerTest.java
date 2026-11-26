package com.job.bootstrap.workflow.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.job.bootstrap.mapper.JobApplicationRecordMapper;
import com.job.bootstrap.mapper.JobUserMapper;
import com.job.bootstrap.service.JobMailSenderService;
import com.job.bootstrap.service.WorkflowTaskProgressService;
import com.job.common.entity.application.JobApplicationRecord;
import com.job.common.entity.user.JobUser;
import com.job.common.entity.workflow.WorkflowTask;
import org.junit.jupiter.api.Test;

import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * InterviewEmailNotifyWorkflowTaskHandler 单元测试。
 *
 * 覆盖重点:
 * 1. 幂等：同一求职记录重复执行（模拟 markFailure 重试），邮件只发一次。
 * 2. 发送失败：回滚幂等状态位并向上抛出，交由调度器重试。
 * 3. 参数缺失：userId/bizId 为空直接抛错，不发送邮件。
 */
class InterviewEmailNotifyWorkflowTaskHandlerTest {

    private static final Long USER_ID = 100L;
    private static final Long BIZ_ID = 55L;
    private static final String EMAIL = "user@example.com";

    private final JobUserMapper jobUserMapper = mock(JobUserMapper.class);
    private final JobApplicationRecordMapper jobApplicationRecordMapper = mock(JobApplicationRecordMapper.class);
    private final JobMailSenderService jobMailSenderService = mock(JobMailSenderService.class);
    private final WorkflowTaskProgressService workflowTaskProgressService = mock(WorkflowTaskProgressService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final InterviewEmailNotifyWorkflowTaskHandler handler = new InterviewEmailNotifyWorkflowTaskHandler(
            jobUserMapper,
            jobApplicationRecordMapper,
            jobMailSenderService,
            workflowTaskProgressService,
            objectMapper
    );

    @Test
    void repeatedExecutionShouldSendEmailOnlyOnce() {
        when(jobUserMapper.selectById(USER_ID)).thenReturn(buildUser());
        when(jobApplicationRecordMapper.selectOne(any())).thenReturn(buildApplication());
        // 第一次抢占成功(1)，第二次已被抢占(0)
        when(jobApplicationRecordMapper.claimInterviewEmailNotify(eq(BIZ_ID), any())).thenReturn(1, 0);

        String first = handler.handle(buildTask());
        String second = handler.handle(buildTask());

        assertThat(first).contains("\"sent\":true");
        assertThat(second).contains("\"sent\":false").contains("already_notified");
        // 关键断言：无论执行几次，邮件只发一次
        verify(jobMailSenderService, times(1)).sendText(eq(EMAIL), anyString(), anyString());
    }

    @Test
    void sendFailureShouldRollbackClaimAndRethrow() {
        when(jobUserMapper.selectById(USER_ID)).thenReturn(buildUser());
        when(jobApplicationRecordMapper.selectOne(any())).thenReturn(buildApplication());
        when(jobApplicationRecordMapper.claimInterviewEmailNotify(eq(BIZ_ID), any())).thenReturn(1);
        doThrow(new RuntimeException("SMTP 连接失败")).when(jobMailSenderService).sendText(anyString(), anyString(), anyString());

        assertThatThrownBy(() -> handler.handle(buildTask()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("SMTP");

        verify(jobApplicationRecordMapper).clearInterviewEmailNotify(eq(BIZ_ID), any());
        verify(jobMailSenderService, times(1)).sendText(anyString(), anyString(), anyString());
    }

    @Test
    void missingUserIdShouldThrowAndNotSend() {
        WorkflowTask task = buildTask();
        task.setUserId(null);
        assertThatThrownBy(() -> handler.handle(task))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(jobMailSenderService);
    }

    private WorkflowTask buildTask() {
        WorkflowTask task = new WorkflowTask();
        task.setId(1L);
        task.setTaskType("INTERVIEW_EMAIL_NOTIFY");
        task.setUserId(USER_ID);
        task.setBizId(BIZ_ID);
        return task;
    }

    private JobUser buildUser() {
        JobUser user = new JobUser();
        user.setId(USER_ID);
        user.setUsername("zhangsan");
        user.setNickname("张三");
        user.setEmail(EMAIL);
        return user;
    }

    private JobApplicationRecord buildApplication() {
        JobApplicationRecord application = new JobApplicationRecord();
        application.setId(BIZ_ID);
        application.setUserId(USER_ID);
        application.setCompanyName("某某科技");
        application.setJobTitle("Java 后端");
        application.setInterviewTime(new Date());
        application.setIsDeleted(0);
        return application;
    }
}
