package com.job.bootstrap.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.job.bootstrap.mcp.McpClientResults.McpExchange;
import com.job.bootstrap.mcp.McpClientResults.McpHandshake;
import com.job.bootstrap.mcp.McpClientResults.McpTools;
import com.job.exception.AgentToolException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * McpClient 单元测试（任务 G6）。
 *
 * 覆盖重点（用 JDK 自带 HttpServer 打桩，不依赖 Spring/外部服务）:
 * 1. initialize / tools/list / tools/call 正常路径，且保留真实协议帧；
 * 2. 服务端返回 JSON-RPC error → McpClientException（code 透传）；
 * 3. 工具业务错误（isError=true）→ AgentToolException；
 * 4. 请求帧包含 jsonrpc/id/method/params，id 单调递增。
 */
class McpClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AtomicReference<String> lastRequestBody = new AtomicReference<>();
    private final AtomicReference<String> responseToReturn = new AtomicReference<>();

    private HttpServer server;
    private McpClient client;

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/mcp", exchange -> {
            lastRequestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] body = (responseToReturn.get() == null ? "{}" : responseToReturn.get())
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(body);
            }
        });
        server.start();

        McpClientProperties properties = new McpClientProperties();
        properties.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/mcp");
        properties.setTimeoutSeconds(5);
        client = new McpClient(properties, objectMapper);
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    private void stubResponse(String json) {
        responseToReturn.set(json);
    }

    @Test
    void initializeShouldParseHandshakeAndKeepFrames() {
        stubResponse("""
                {"jsonrpc":"2.0","id":1,"result":{"protocolVersion":"2024-11-05","capabilities":{"tools":{}},"serverInfo":{"name":"job-agent-mcp","version":"1.0.0"}}}
                """);

        McpExchange<McpHandshake> exchange = client.initialize();

        McpHandshake handshake = exchange.result();
        assertThat(handshake.protocolVersion()).isEqualTo("2024-11-05");
        assertThat(handshake.serverName()).isEqualTo("job-agent-mcp");
        assertThat(handshake.serverVersion()).isEqualTo("1.0.0");
        // 真实协议帧被保留，后台可直接展示
        assertThat(exchange.requestJson()).contains("\"method\":\"initialize\"");
        assertThat(exchange.responseJson()).contains("job-agent-mcp");
    }

    @Test
    void listToolsShouldReturnToolDefinitions() {
        stubResponse("""
                {"jsonrpc":"2.0","id":2,"result":{"tools":[{"name":"RecruitmentPlatformConnectorTool.searchExternalJobs","description":"搜索岗位","inputSchema":{"type":"object"}}]}}
                """);

        McpExchange<McpTools> exchange = client.listTools();

        List<JsonNode> tools = exchange.result().tools();
        assertThat(tools).hasSize(1);
        assertThat(tools.get(0).path("name").asText())
                .isEqualTo("RecruitmentPlatformConnectorTool.searchExternalJobs");
    }

    @Test
    void callToolShouldReturnTextContent() {
        stubResponse("""
                {"jsonrpc":"2.0","id":3,"result":{"content":[{"type":"text","text":"{\\"status\\":\\"PREVIEW\\"}"}],"isError":false}}
                """);

        McpExchange<String> exchange = client.callTool(
                "RecruitmentPlatformConnectorTool.searchExternalJobs",
                java.util.Map.of("providerCode", "boss", "keyword", "Java"));

        assertThat(exchange.result()).isEqualTo("{\"status\":\"PREVIEW\"}");
        assertThat(exchange.requestJson()).contains("tools/call");
        assertThat(exchange.requestJson()).contains("RecruitmentPlatformConnectorTool.searchExternalJobs");
    }

    @Test
    void protocolErrorShouldMapToClientException() {
        stubResponse("""
                {"jsonrpc":"2.0","id":4,"error":{"code":-32601,"message":"未知方法: x"}}
                """);

        assertThatThrownBy(() -> client.listTools())
                .isInstanceOf(McpClientException.class)
                .satisfies(exception -> assertThat(((McpClientException) exception).getCode()).isEqualTo(-32601));
    }

    @Test
    void toolBusinessErrorShouldMapToAgentToolException() {
        stubResponse("""
                {"jsonrpc":"2.0","id":5,"result":{"content":[{"type":"text","text":"连接器执行失败"}],"isError":true}}
                """);

        assertThatThrownBy(() -> client.callTool("RecruitmentPlatformConnectorTool.searchExternalJobs", java.util.Map.of()))
                .isInstanceOf(AgentToolException.class)
                .hasMessageContaining("连接器执行失败");
    }

    @Test
    void requestIdShouldIncreaseMonotonically() throws Exception {
        stubResponse("""
                {"jsonrpc":"2.0","id":1,"result":{}}
                """);
        client.initialize();
        String firstRequest = lastRequestBody.get();
        client.initialize();
        String secondRequest = lastRequestBody.get();

        long firstId = objectMapper.readTree(firstRequest).path("id").asLong();
        long secondId = objectMapper.readTree(secondRequest).path("id").asLong();
        assertThat(secondId).isGreaterThan(firstId);
    }
}
