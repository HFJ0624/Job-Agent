package com.job.bootstrap.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.job.bootstrap.mcp.McpClientResults.McpExchange;
import com.job.bootstrap.mcp.McpClientResults.McpHandshake;
import com.job.bootstrap.mcp.McpClientResults.McpTools;
import com.job.enums.AgentToolErrorCode;
import com.job.exception.AgentToolException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 功能: MCP 客户端（任务 G6，手写 JSON-RPC 2.0 over HTTP）。
 *
 * 设计说明:
 * 1. 不引入官方 MCP SDK：协议子集（initialize/tools/list/tools/call）足够当前场景，
 *    手写 + 显式错误码映射让行为完全可控、可单测（与 G5 手写 SSE 同一工程风格）；
 * 2. 每次交换保留原始请求/响应帧，后台可展示真实协议数据；
 * 3. 错误分级：协议错误（error.code）映射为 McpClientException；工具业务错误
 *    （isError=true）映射为 AgentToolException，与本地工具失败语义一致；
 * 4. 请求 id 单调递增，便于把请求与响应对应起来。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@EnableConfigurationProperties(McpClientProperties.class)
public class McpClient {

    private final McpClientProperties properties;
    private final ObjectMapper objectMapper;

    private final AtomicLong idSequence = new AtomicLong(1L);

    /** MCP 握手。 */
    public McpExchange<McpHandshake> initialize() {
        McpExchange<JsonNode> exchange = post("initialize", Map.of());
        JsonNode result = exchange.result();
        return new McpExchange<>(
                exchange.requestJson(),
                exchange.responseJson(),
                new McpHandshake(
                        result.path("protocolVersion").asText(""),
                        result.path("serverInfo").path("name").asText(""),
                        result.path("serverInfo").path("version").asText("")
                )
        );
    }

    /** 列出服务端工具。 */
    public McpExchange<McpTools> listTools() {
        McpExchange<JsonNode> exchange = post("tools/list", Map.of());
        List<JsonNode> tools = new ArrayList<>();
        JsonNode toolsNode = exchange.result().path("tools");
        if (toolsNode.isArray()) {
            toolsNode.forEach(tools::add);
        }
        return new McpExchange<>(exchange.requestJson(), exchange.responseJson(), new McpTools(tools));
    }

    /**
     * 调用服务端工具，返回工具输出的文本内容。
     *
     * @param name      工具名
     * @param arguments 工具入参
     * @return 协议交换（含原始帧与文本结果）
     * @throws AgentToolException 工具业务错误（isError=true）或协议错误
     */
    public McpExchange<String> callTool(String name, Map<String, Object> arguments) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("name", name);
        params.put("arguments", arguments == null ? Map.of() : arguments);

        McpExchange<JsonNode> exchange = post("tools/call", params);
        JsonNode result = exchange.result();
        boolean isError = result.path("isError").asBoolean(false);
        String text = "";
        JsonNode content = result.path("content");
        if (content.isArray() && !content.isEmpty()) {
            text = content.get(0).path("text").asText("");
        }
        if (isError) {
            throw new AgentToolException(
                    AgentToolErrorCode.TOOL_EXECUTION_FAILED,
                    name,
                    "MCP 工具返回错误: " + text
            );
        }
        return new McpExchange<>(exchange.requestJson(), exchange.responseJson(), text);
    }

    // 发送一次 JSON-RPC 请求并解析 result；协议错误按 code 抛出 McpClientException
    private McpExchange<JsonNode> post(String method, Map<String, Object> params) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("jsonrpc", "2.0");
        request.put("id", idSequence.getAndIncrement());
        request.put("method", method);
        request.put("params", params);

        String requestJson;
        String responseJson;
        try {
            requestJson = objectMapper.writeValueAsString(request);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            ResponseEntity<JsonNode> response = getRestTemplate().postForEntity(
                    properties.getBaseUrl(),
                    new HttpEntity<>(request, headers),
                    JsonNode.class
            );
            JsonNode body = response.getBody();
            responseJson = body == null ? "" : body.toString();

            if (body == null || !body.has("result")) {
                int code = body == null ? -1 : body.path("error").path("code").asInt(-1);
                String message = body == null
                        ? "MCP 服务端无响应"
                        : body.path("error").path("message").asText("未知协议错误");
                throw new McpClientException(code, message);
            }
            return new McpExchange<>(requestJson, responseJson, body.path("result"));
        } catch (McpClientException exception) {
            throw exception;
        } catch (Exception exception) {
            log.warn("MCP 调用失败，method={}, error={}", method, exception.getMessage());
            throw new McpClientException(-1, "MCP 调用失败: " + exception.getMessage());
        }
    }

    private RestTemplate getRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        Duration timeout = Duration.ofSeconds(Math.max(1, properties.getTimeoutSeconds()));
        factory.setConnectTimeout(timeout);
        factory.setReadTimeout(timeout);
        return new RestTemplate(factory);
    }
}
