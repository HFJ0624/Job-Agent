package com.job.bootstrap.controller.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.job.mcp.protocol.McpErrorCodes;
import com.job.mcp.protocol.McpProtocolHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 功能: MCP 协议 HTTP 端点（任务 G6，服务端出口）。
 *
 * 设计说明:
 * 1. 单端点 POST /mcp：JSON-RPC 2.0 over HTTP（streamable HTTP 的简化形态——
 *    每次请求一个 JSON 响应，通知无响应体），覆盖 initialize/tools/list/tools/call/ping；
 * 2. 协议逻辑全部委托 McpProtocolHandler（纯 JSON、可单测），本类只做 HTTP 适配；
 * 3. 请求体不是合法 JSON 时，按 JSON-RPC 规范返回 -32700，而不是 Spring 默认 400 文本。
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/mcp")
public class McpProtocolController {

    private final McpProtocolHandler mcpProtocolHandler;
    private final ObjectMapper objectMapper;

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<JsonNode> handle(@RequestBody(required = false) JsonNode request) {
        if (request == null) {
            return ResponseEntity.badRequest().body(errorBody(McpErrorCodes.INVALID_REQUEST, "请求体为空"));
        }
        JsonNode response = mcpProtocolHandler.handle(request);
        if (response == null) {
            return ResponseEntity.noContent().build(); // 通知类请求：无响应体
        }
        return ResponseEntity.ok(response);
    }

    // 请求体 JSON 解析失败 → JSON-RPC -32700（Parse error）
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<JsonNode> handleParseError(HttpMessageNotReadableException exception) {
        log.debug("MCP 请求体解析失败，error={}", exception.getMessage());
        return ResponseEntity.badRequest().body(errorBody(McpErrorCodes.PARSE_ERROR, "JSON 解析失败"));
    }

    private JsonNode errorBody(int code, String message) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("code", code);
        error.put("message", message);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("jsonrpc", "2.0");
        response.put("id", null);
        response.put("error", error);
        return objectMapper.valueToTree(response);
    }
}
