package com.job.mcp.protocol;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.job.mcp.connector.tool.CalendarConnectorTool;
import com.job.mcp.connector.tool.EmailConnectorTool;
import com.job.mcp.connector.tool.JobSourceSyncConnectorTool;
import com.job.mcp.connector.tool.NotificationConnectorTool;
import com.job.mcp.connector.tool.RecruitmentPlatformConnectorTool;
import com.job.mcp.connector.tool.ResumeExportConnectorTool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * McpProtocolHandler 单元测试（任务 G6）。
 *
 * 覆盖重点:
 * 1. initialize / ping / tools/list / tools/call 正常路径；
 * 2. 未知方法 -32601、缺参数/未知工具 -32602、非请求对象 -32600；
 * 3. 通知（无 id）不产生响应；
 * 4. tools/call 的 content 结构与 isError 语义。
 */
class McpProtocolHandlerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private McpProtocolHandler handler;

    @BeforeEach
    void setUp() {
        // 连接器工具是无依赖 Spring 组件，直接构造即可
        handler = new McpProtocolHandler(
                objectMapper,
                new RecruitmentPlatformConnectorTool(),
                new EmailConnectorTool(),
                new CalendarConnectorTool(),
                new NotificationConnectorTool(),
                new ResumeExportConnectorTool(),
                new JobSourceSyncConnectorTool()
        );
    }

    private JsonNode handle(String json) throws Exception {
        return handler.handle(objectMapper.readTree(json));
    }

    @Test
    void initializeShouldReturnProtocolAndServerInfo() throws Exception {
        JsonNode response = handle("""
                {"jsonrpc":"2.0","id":1,"method":"initialize","params":{}}
                """);

        assertThat(response.path("id").asInt()).isEqualTo(1);
        assertThat(response.path("result").path("protocolVersion").asText()).isEqualTo("2024-11-05");
        assertThat(response.path("result").path("serverInfo").path("name").asText()).isEqualTo("job-agent-mcp");
        assertThat(response.path("result").path("capabilities").path("tools").isObject()).isTrue();
    }

    @Test
    void pingShouldReturnEmptyResult() throws Exception {
        JsonNode response = handle("""
                {"jsonrpc":"2.0","id":"abc","method":"ping"}
                """);

        assertThat(response.path("id").asText()).isEqualTo("abc");
        assertThat(response.path("result").isObject()).isTrue();
        assertThat(response.path("result").isEmpty()).isTrue();
    }

    @Test
    void toolsListShouldReturnAllConnectorTools() throws Exception {
        JsonNode response = handle("""
                {"jsonrpc":"2.0","id":2,"method":"tools/list"}
                """);

        JsonNode tools = response.path("result").path("tools");
        assertThat(tools.isArray()).isTrue();
        assertThat(tools).hasSize(7);
        assertThat(tools.get(0).path("name").asText())
                .isEqualTo("RecruitmentPlatformConnectorTool.searchExternalJobs");
        assertThat(tools.get(0).path("inputSchema").path("properties").path("keyword").path("type").asText()).isEqualTo("string");
    }

    @Test
    void toolsCallShouldReturnContentAndNotError() throws Exception {
        JsonNode response = handle("""
                {"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"RecruitmentPlatformConnectorTool.searchExternalJobs","arguments":{"providerCode":"boss","keyword":"Java","city":"上海","limit":5}}}
                """);

        assertThat(response.path("result").path("isError").asBoolean()).isFalse();
        JsonNode content = response.path("result").path("content");
        assertThat(content).hasSize(1);
        assertThat(content.get(0).path("type").asText()).isEqualTo("text");
        // 连接器第一版返回 PREVIEW 响应，文本里应包含工具名
        assertThat(content.get(0).path("text").asText())
                .contains("RecruitmentPlatformConnectorTool.searchExternalJobs");
    }

    @Test
    void unknownMethodShouldReturnMethodNotFound() throws Exception {
        JsonNode response = handle("""
                {"jsonrpc":"2.0","id":4,"method":"resources/list"}
                """);

        assertThat(response.path("error").path("code").asInt()).isEqualTo(McpErrorCodes.METHOD_NOT_FOUND);
        assertThat(response.path("error").path("message").asText()).contains("未知方法");
    }

    @Test
    void toolsCallWithoutNameShouldReturnInvalidParams() throws Exception {
        JsonNode response = handle("""
                {"jsonrpc":"2.0","id":5,"method":"tools/call","params":{}}
                """);

        assertThat(response.path("error").path("code").asInt()).isEqualTo(McpErrorCodes.INVALID_PARAMS);
    }

    @Test
    void toolsCallUnknownToolShouldReturnInvalidParams() throws Exception {
        JsonNode response = handle("""
                {"jsonrpc":"2.0","id":6,"method":"tools/call","params":{"name":"EvilTool.hack","arguments":{}}}
                """);

        assertThat(response.path("error").path("code").asInt()).isEqualTo(McpErrorCodes.INVALID_PARAMS);
        assertThat(response.path("error").path("message").asText()).contains("未知工具");
    }

    @Test
    void missingMethodShouldReturnInvalidRequest() throws Exception {
        JsonNode response = handle("""
                {"jsonrpc":"2.0","id":7}
                """);

        assertThat(response.path("error").path("code").asInt()).isEqualTo(McpErrorCodes.INVALID_REQUEST);
    }

    @Test
    void nonObjectRequestShouldReturnInvalidRequest() {
        JsonNode response = handler.handle(objectMapper.createArrayNode());

        assertThat(response.path("error").path("code").asInt()).isEqualTo(McpErrorCodes.INVALID_REQUEST);
    }

    @Test
    void notificationShouldNotProduceResponse() throws Exception {
        // 无 id 的通知：正常处理但不响应
        assertThat(handle("""
                {"jsonrpc":"2.0","method":"ping"}
                """)).isNull();
        // 通知类错误同样不响应
        assertThat(handle("""
                {"jsonrpc":"2.0","method":"unknown/method"}
                """)).isNull();
    }
}
