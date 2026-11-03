package com.job.bootstrap.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.job.bootstrap.ai.turn.AiChatMessage;
import com.job.bootstrap.ai.turn.AiChatTurnRequest;
import com.job.bootstrap.ai.turn.AiChatTurnResponse;
import com.job.bootstrap.ai.turn.AiToolCall;
import com.job.bootstrap.ai.turn.AiToolSpec;
import com.job.common.entity.ai.AiModelConfig;
import com.job.exception.BizException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 结构化轮次协议测试（第一阶段）。
 *
 * 覆盖重点（对应文档“模型协议”一节）:
 * 1. 请求体：assistant tool_calls 与 tool 消息按 toolCallId 完整序列化、工具清单按 function 包装；
 * 2. 响应：content=null 且只有 tool_calls 是合法响应；多工具调用全部解析；
 * 3. 空响应（既无 content 也无 tool_calls）→ 协议失败抛错；
 * 4. finish_reason=length（输出截断）→ 标记 truncated，由 Runner 决定修正；
 * 5. 无工具名的调用被丢弃（不可执行）。
 */
class RestTemplateAiModelHttpClientTurnTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplateAiModelHttpClient client = new RestTemplateAiModelHttpClient(objectMapper);

    private AiModelConfig model() {
        AiModelConfig model = new AiModelConfig();
        model.setModelIdentifier("deepseek-chat");
        model.setTemperature(new java.math.BigDecimal("0.3"));
        model.setMaxTokens(1024);
        return model;
    }

    @Test
    void requestBodyShouldSerializeFullMessageSequence() {
        AiChatTurnRequest request = AiChatTurnRequest.of(
                List.of(
                        AiChatMessage.system("你是岗位搜索助手"),
                        AiChatMessage.user("找上海Java岗位"),
                        AiChatMessage.assistant(null, List.of(new AiToolCall("call-1", "job_search", "{\"city\":\"上海\"}"))),
                        AiChatMessage.tool("call-1", "{\"items\":[]}")
                ),
                List.of(new AiToolSpec("job_search", "搜索岗位",
                        Map.of("type", "object", "properties", Map.of("city", Map.of("type", "string")))))
        );

        Map<String, Object> body = client.buildTurnRequestBody(model(), request);

        assertThat(body.get("model")).isEqualTo("deepseek-chat");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> messages = (List<Map<String, Object>>) body.get("messages");
        assertThat(messages).hasSize(4);
        // assistant 的 tool_calls 序列化
        Map<String, Object> assistant = messages.get(2);
        assertThat(assistant.get("role")).isEqualTo("assistant");
        assertThat(assistant).doesNotContainKey("content");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> calls = (List<Map<String, Object>>) assistant.get("tool_calls");
        assertThat(calls).hasSize(1);
        assertThat(calls.get(0).get("id")).isEqualTo("call-1");
        assertThat(calls.get(0).get("type")).isEqualTo("function");
        // tool 消息按 toolCallId 配对
        assertThat(messages.get(3).get("role")).isEqualTo("tool");
        assertThat(messages.get(3).get("tool_call_id")).isEqualTo("call-1");
        // 工具清单按 function 包装，并带 tool_choice
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> tools = (List<Map<String, Object>>) body.get("tools");
        assertThat(tools).hasSize(1);
        assertThat(tools.get(0).get("type")).isEqualTo("function");
        assertThat(body.get("tool_choice")).isEqualTo("auto");
    }

    @Test
    void contentNullWithToolCallsShouldBeLegal() {
        String responseBody = """
                {
                  "choices": [{
                    "message": {
                      "role": "assistant",
                      "content": null,
                      "tool_calls": [
                        {"id": "call-a", "type": "function", "function": {"name": "job_search", "arguments": "{\\"city\\":\\"上海\\"}"}},
                        {"id": "call-b", "type": "function", "function": {"name": "job_recommend", "arguments": "{\\"limit\\":5}"}}
                      ]
                    },
                    "finish_reason": "tool_calls"
                  }],
                  "usage": {"prompt_tokens": 120, "completion_tokens": 30}
                }
                """;

        AiChatTurnResponse response = client.parseTurnResponse(responseBody);

        assertThat(response.content()).isNull();
        assertThat(response.hasToolCalls()).isTrue();
        assertThat(response.toolCalls()).hasSize(2);
        assertThat(response.toolCalls().get(0).name()).isEqualTo("job_search");
        assertThat(response.toolCalls().get(1).name()).isEqualTo("job_recommend");
        assertThat(response.finishReason()).isEqualTo("tool_calls");
        assertThat(response.inputTokens()).isEqualTo(120);
        assertThat(response.outputTokens()).isEqualTo(30);
        assertThat(response.truncated()).isFalse();
    }

    @Test
    void emptyResponseShouldFailProtocol() {
        String responseBody = """
                {"choices": [{"message": {"role": "assistant", "content": null}, "finish_reason": "stop"}]}
                """;

        assertThatThrownBy(() -> client.parseTurnResponse(responseBody))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("tool_calls");
    }

    @Test
    void truncatedResponseShouldBeMarked() {
        String responseBody = """
                {"choices": [{"message": {"role": "assistant", "content": "{\\"outcome\\":\\"FOUND\\""}, "finish_reason": "length"}]}
                """;

        AiChatTurnResponse response = client.parseTurnResponse(responseBody);

        assertThat(response.truncated()).isTrue();
    }

    @Test
    void toolCallWithoutNameShouldBeDropped() {
        String responseBody = """
                {"choices": [{"message": {"role": "assistant", "content": null,
                  "tool_calls": [
                    {"id": "bad", "type": "function", "function": {"arguments": "{}"}},
                    {"id": "good", "type": "function", "function": {"name": "job_search", "arguments": "{}"}}
                  ]}, "finish_reason": "tool_calls"}]}
                """;

        AiChatTurnResponse response = client.parseTurnResponse(responseBody);

        assertThat(response.toolCalls()).hasSize(1);
        assertThat(response.toolCalls().get(0).id()).isEqualTo("good");
    }
}
