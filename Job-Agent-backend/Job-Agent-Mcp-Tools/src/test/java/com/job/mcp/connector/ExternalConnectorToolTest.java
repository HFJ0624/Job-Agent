package com.job.mcp.connector;

import cn.hutool.json.JSONUtil;
import com.job.mcp.connector.tool.CalendarConnectorTool;
import com.job.mcp.connector.tool.EmailConnectorTool;
import com.job.mcp.connector.tool.JobSourceSyncConnectorTool;
import com.job.mcp.connector.tool.NotificationConnectorTool;
import com.job.mcp.connector.tool.RecruitmentPlatformConnectorTool;
import com.job.mcp.connector.tool.ResumeExportConnectorTool;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ExternalConnectorToolTest {

    @Test
    void shouldPreviewRecruitmentPlatformSearchWithoutCallingExternalApi() {
        RecruitmentPlatformConnectorTool tool = new RecruitmentPlatformConnectorTool();

        String json = tool.searchExternalJobs("boss", "Java", "上海", 10);

        assertThat(JSONUtil.parseObj(json).getStr("toolName")).isEqualTo("RecruitmentPlatformConnectorTool.searchExternalJobs");
        assertThat(JSONUtil.parseObj(json).getStr("status")).isEqualTo("PREVIEW");
        assertThat(JSONUtil.parseObj(json).getBool("requiresRealAdapter")).isTrue();
    }

    @Test
    void shouldReturnEmailConnectorPreviewForReadAndSend() {
        EmailConnectorTool tool = new EmailConnectorTool();

        String readJson = tool.readEmails("qq-mail", "面试", 5);
        String sendJson = tool.sendEmail("qq-mail", "hr@example.com", "确认面试", "我可以参加面试");

        assertThat(JSONUtil.parseObj(readJson).getStr("toolName")).isEqualTo("EmailConnectorTool.readEmails");
        assertThat(JSONUtil.parseObj(readJson).getStr("sideEffectType")).isEqualTo("READ");
        assertThat(JSONUtil.parseObj(sendJson).getStr("toolName")).isEqualTo("EmailConnectorTool.sendEmail");
        assertThat(JSONUtil.parseObj(sendJson).getBool("requiresUserConfirmation")).isTrue();
    }

    @Test
    void shouldReturnCalendarNotificationResumeAndSyncPreview() {
        CalendarConnectorTool calendarTool = new CalendarConnectorTool();
        NotificationConnectorTool notificationTool = new NotificationConnectorTool();
        ResumeExportConnectorTool resumeExportTool = new ResumeExportConnectorTool();
        JobSourceSyncConnectorTool syncTool = new JobSourceSyncConnectorTool();

        String calendarJson = calendarTool.createInterviewEvent("google-calendar", "后端面试", "2026-07-01 10:00", "线上");
        String notificationJson = notificationTool.sendNotification("email", "user-1", "面试提醒", "明天 10 点面试");
        String exportJson = resumeExportTool.exportResume(1001L, "PDF");
        String syncJson = syncTool.syncJobs("boss", "Java", "上海", 20);

        assertThat(JSONUtil.parseObj(calendarJson).getBool("requiresUserConfirmation")).isTrue();
        assertThat(JSONUtil.parseObj(notificationJson).getStr("toolName")).isEqualTo("NotificationConnectorTool.sendNotification");
        assertThat(JSONUtil.parseObj(exportJson).getStr("toolName")).isEqualTo("ResumeExportConnectorTool.exportResume");
        assertThat(JSONUtil.parseObj(syncJson).getStr("toolName")).isEqualTo("JobSourceSyncConnectorTool.syncJobs");
    }
}
