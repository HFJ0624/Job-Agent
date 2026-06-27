package com.job.bootstrap.agent.connector;

import cn.hutool.json.JSONUtil;
import com.job.bootstrap.agent.schema.AgentToolSchemaRegistry;
import com.job.bootstrap.service.AdminExternalConnectorService;
import com.job.bootstrap.service.impl.AdminExternalConnectorServiceImpl;
import com.job.common.agent.tool.AgentToolSchema;
import com.job.enums.AgentToolConfirmationType;
import com.job.enums.AgentToolSideEffectType;
import com.job.mcp.connector.tool.CalendarConnectorTool;
import com.job.mcp.connector.tool.EmailConnectorTool;
import com.job.mcp.connector.tool.JobSourceSyncConnectorTool;
import com.job.mcp.connector.tool.NotificationConnectorTool;
import com.job.mcp.connector.tool.RecruitmentPlatformConnectorTool;
import com.job.mcp.connector.tool.ResumeExportConnectorTool;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ExternalConnectorIntegrationTest {

    private final AgentToolSchemaRegistry registry = new AgentToolSchemaRegistry();

    @Test
    void shouldRegisterExternalConnectorSchemas() {
        AgentToolSchema readEmail = registry.getRequired("EmailConnectorTool.readEmails");
        AgentToolSchema sendEmail = registry.getRequired("EmailConnectorTool.sendEmail");
        AgentToolSchema syncJobs = registry.getRequired("JobSourceSyncConnectorTool.syncJobs");

        assertThat(readEmail.getCategory()).isEqualTo("external_connector");
        assertThat(readEmail.getSideEffectType()).isEqualTo(AgentToolSideEffectType.READ_ONLY);
        assertThat(readEmail.getRequiresUserConfirmation()).isFalse();
        assertThat(sendEmail.getSideEffectType()).isEqualTo(AgentToolSideEffectType.EXTERNAL_ACTION);
        assertThat(sendEmail.getConfirmationType()).isEqualTo(AgentToolConfirmationType.REQUIRED_BEFORE_EXECUTION);
        assertThat(syncJobs.getRequiresUserConfirmation()).isTrue();
    }

    @Test
    void shouldPreviewExternalConnectorToolByCanonicalToolName() {
        AdminExternalConnectorService service = new AdminExternalConnectorServiceImpl(
                registry,
                new RecruitmentPlatformConnectorTool(),
                new EmailConnectorTool(),
                new CalendarConnectorTool(),
                new NotificationConnectorTool(),
                new ResumeExportConnectorTool(),
                new JobSourceSyncConnectorTool()
        );

        String result = service.preview("EmailConnectorTool.readEmails", Map.of(
                "providerCode", "qq-mail",
                "keyword", "面试",
                "limit", 5
        ));

        assertThat(JSONUtil.parseObj(result).getStr("toolName")).isEqualTo("EmailConnectorTool.readEmails");
        assertThat(JSONUtil.parseObj(result).getStr("status")).isEqualTo("PREVIEW");
        assertThat(JSONUtil.parseObj(result).getBool("requiresRealAdapter")).isTrue();
    }
}
