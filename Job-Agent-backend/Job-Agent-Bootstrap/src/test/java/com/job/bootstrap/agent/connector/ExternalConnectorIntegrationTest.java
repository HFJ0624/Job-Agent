package com.job.bootstrap.agent.connector;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

/**
 * 外部连接器 Schema 注册与预览冒烟测试。
 *
 * 说明：原实现依赖 hutool 的 JSONUtil 解析 preview 返回的 JSON 字符串，
 * 但 hutool 在 Bootstrap 模块并非显式依赖（仅靠传递依赖存在，环境不稳定）。
 * 这里改为使用 Spring Test 已自带的 Jackson 解析，避免对 hutool 的隐式依赖。
 */
class ExternalConnectorIntegrationTest {

    private final AgentToolSchemaRegistry registry = new AgentToolSchemaRegistry();
    private final ObjectMapper objectMapper = new ObjectMapper();

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
    void shouldPreviewExternalConnectorToolByCanonicalToolName() throws Exception {
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

        JsonNode node = objectMapper.readTree(result);
        assertThat(node.get("toolName").asText()).isEqualTo("EmailConnectorTool.readEmails");
        assertThat(node.get("status").asText()).isEqualTo("PREVIEW");
        assertThat(node.get("requiresRealAdapter").asBoolean()).isTrue();
    }
}
