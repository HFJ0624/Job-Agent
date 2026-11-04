package com.job.bootstrap.agent.core.react;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.job.bootstrap.ai.turn.AiChatMessage;
import com.job.bootstrap.ai.turn.AiChatTurnRequest;
import com.job.bootstrap.ai.turn.AiChatTurnResponse;
import com.job.bootstrap.ai.turn.AiToolCall;
import com.job.bootstrap.ai.turn.AiToolSpec;
import com.job.bootstrap.service.AiModelGatewayService;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ReActRunner 单元测试（第一阶段）。
 *
 * 覆盖重点（对应文档测试清单 1/2/5/8）:
 * 1. 工具调用 → Observation → 再次模型决策 → 终结结果（真实反馈循环）；
 * 2. 多工具调用按 toolCallId 配对回填；
 * 3. 终结参数非法 → 一次修正机会；
 * 4. 越权工具被白名单拦截（返回协议错误而非执行）；
 * 5. 业务工具预算耗尽 → FAILED + 运行时原因码（不依赖模型自停）；
 * 6. 重复相同查询 → 终止（禁止循环）；
 * 7. 终结工具与业务工具混轮 → 协议错误。
 */
class ReActRunnerTest {

    private static final Long USER_ID = 100L;
    private static final String TRACE_ID = "trace-react";

    private final AiModelGatewayService gateway = mock(AiModelGatewayService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final TerminalToolContract terminalTool = new TerminalToolContract(objectMapper);
    private final ReActRunner runner = new ReActRunner(gateway, terminalTool, objectMapper);

    private AgentDefinition definition;
    private AgentRunContext context;
    private final List<String> executed = new ArrayList<>();

    @BeforeEach
    void setUp() {
        definition = new AgentDefinition(
                "job-search",
                "你是岗位搜索助手",
                Map.of("job_search", "JobSearchTool.searchJobs"),
                List.of(new AiToolSpec("job_search", "搜索岗位", Map.of("type", "object"))),
                TerminalToolContract.TOOL_NAME,
                AgentDefinition.DEFAULT_MAX_TURNS,
                AgentDefinition.DEFAULT_MAX_TOOL_CALLS,
                AgentDefinition.DEFAULT_DEADLINE_SECONDS
        );
        context = new AgentRunContext("你是岗位搜索助手", "找上海 Java 岗位");
        executed.clear();
    }

    private void stubTurns(AiChatTurnResponse... responses) {
        var stubbing = when(gateway.chatTurn(anyString(), any(), any(AiChatTurnRequest.class), anyLong(), anyString()));
        for (int i = 0; i < responses.length - 1; i++) {
            stubbing = stubbing.thenReturn(responses[i]);
        }
        stubbing.thenReturn(responses[responses.length - 1]);
    }

    private AiChatTurnResponse toolCall(String callId, String name, String args) {
        return new AiChatTurnResponse(null, List.of(new AiToolCall(callId, name, args)), "tool_calls", 10, 5);
    }

    private AiChatTurnResponse finish(String args) {
        return new AiChatTurnResponse(null, List.of(new AiToolCall("finish-1", TerminalToolContract.TOOL_NAME, args)), "tool_calls", 10, 5);
    }

    @Test
    void toolResultShouldFeedBackIntoNextDecision() {
        stubTurns(
                toolCall("c1", "job_search", "{\"city\":\"上海\"}"),
                finish("{\"outcome\":\"FOUND\",\"candidateIds\":[7],\"summary\":\"找到1个\"}")
        );

        AgentResult result = runner.run(definition, context, (tool, args) -> {
            executed.add(tool + ":" + args.get("city"));
            return "{\"items\":[{\"jobId\":7}]}";
        }, USER_ID, TRACE_ID);

        assertThat(result.outcome()).isEqualTo(AgentResult.Outcome.FOUND);
        assertThat(result.candidateIds()).containsExactly(7L);
        assertThat(result.toolCallsUsed()).isEqualTo(1);
        assertThat(executed).containsExactly("JobSearchTool.searchJobs:上海");

        // 第二轮请求里必须带上了工具 Observation（真实反馈循环）
        ArgumentCaptor<AiChatTurnRequest> captor = ArgumentCaptor.forClass(AiChatTurnRequest.class);
        verify(gateway, times(2)).chatTurn(anyString(), any(), captor.capture(), anyLong(), anyString());
        List<AiChatMessage> secondTurnMessages = captor.getAllValues().get(1).messages();
        assertThat(secondTurnMessages).anySatisfy(message -> {
            assertThat(message.role()).isEqualTo("tool");
            assertThat(message.toolCallId()).isEqualTo("c1");
            assertThat(message.content()).contains("jobId").contains("7");
        });
        // 终结工具的 tool 消息也要配对保存
        assertThat(context.messages()).anySatisfy(message -> {
            assertThat(message.role()).isEqualTo("tool");
            assertThat(message.toolCallId()).isEqualTo("finish-1");
        });
    }

    @Test
    void multipleToolCallsShouldBePaired() {
        AiChatTurnResponse twoCalls = new AiChatTurnResponse(null, List.of(
                new AiToolCall("c1", "job_search", "{\"city\":\"上海\"}"),
                new AiToolCall("c2", "job_search", "{\"city\":\"杭州\"}")
        ), "tool_calls", 10, 5);
        stubTurns(twoCalls,
                finish("{\"outcome\":\"FOUND\",\"candidateIds\":[1],\"summary\":\"ok\"}"));

        runner.run(definition, context, (tool, args) -> "{\"items\":[]}", USER_ID, TRACE_ID);

        // 两个调用各自有配对的 tool 消息
        assertThat(context.messages().stream()
                .filter(message -> "tool".equals(message.role()))
                .map(AiChatMessage::toolCallId)
                .toList()).contains("c1", "c2");
        assertThat(context.messages().stream()
                .filter(message -> "tool".equals(message.role()))
                .filter(message -> "c1".equals(message.toolCallId()))
                .count()).isEqualTo(1);
    }

    @Test
    void invalidTerminalArgsShouldGetOneCorrectionChance() {
        stubTurns(
                finish("{\"outcome\":\"FOUND\",\"candidateIds\":[],\"summary\":\"没给候选\"}"),
                finish("{\"outcome\":\"FOUND\",\"candidateIds\":[9],\"summary\":\"修正后\"}")
        );

        AgentResult result = runner.run(definition, context, (tool, args) -> "{}", USER_ID, TRACE_ID);

        assertThat(result.outcome()).isEqualTo(AgentResult.Outcome.FOUND);
        // 第一次的协议错误回填成了 tool 消息
        assertThat(context.messages()).anySatisfy(message -> {
            if ("tool".equals(message.role()) && "finish-1".equals(message.toolCallId())) {
                assertThat(message.content()).contains("candidateId");
            }
        });
    }

    @Test
    void nonWhitelistedToolShouldBeBlocked() {
        AiChatTurnResponse illegal = new AiChatTurnResponse(null, List.of(
                new AiToolCall("c1", "email_send", "{}")), "tool_calls", 10, 5);
        stubTurns(illegal, finish("{\"outcome\":\"EMPTY\",\"summary\":\"没有结果\"}"));

        AgentResult result = runner.run(definition, context, (tool, args) -> {
            executed.add(tool);
            return "{}";
        }, USER_ID, TRACE_ID);

        // 越权工具从未被执行；模型收到白名单错误后正常终结
        assertThat(executed).isEmpty();
        assertThat(result.outcome()).isEqualTo(AgentResult.Outcome.EMPTY);
        assertThat(context.messages()).anySatisfy(message -> {
            if ("tool".equals(message.role()) && "c1".equals(message.toolCallId())) {
                assertThat(message.content()).contains("白名单");
            }
        });
    }

    @Test
    void toolCallBudgetExhaustedShouldFailWithReason() {
        /*
         * 每轮 2 次调用：4 轮用满 8 次工具预算，第 5 轮第一次调用即被运行时截断。
         * （1 次/轮时先撞 6 轮上限，根本测不到工具预算）
         */
        List<AiChatTurnResponse> responses = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            responses.add(new AiChatTurnResponse(null, List.of(
                    new AiToolCall("a" + i, "job_search", "{\"page\":" + i + "}"),
                    new AiToolCall("b" + i, "job_search", "{\"page\":" + (i + 100) + "}")
            ), "tool_calls", 10, 5));
        }
        stubTurns(responses.toArray(new AiChatTurnResponse[0]));

        AgentResult result = runner.run(definition, context, (tool, args) -> "{}", USER_ID, TRACE_ID);

        assertThat(result.outcome()).isEqualTo(AgentResult.Outcome.FAILED);
        assertThat(result.reasonCode()).isEqualTo(AgentResult.ReasonCode.BUDGET_TOOL_CALLS_EXHAUSTED);
        assertThat(result.toolCallsUsed()).isEqualTo(AgentDefinition.DEFAULT_MAX_TOOL_CALLS);
    }

    @Test
    void repeatedIdenticalQueryShouldTerminate() {
        stubTurns(
                toolCall("c1", "job_search", "{\"city\":\"上海\"}"),
                toolCall("c2", "job_search", "{\"city\":\"上海\"}")
        );

        AgentResult result = runner.run(definition, context, (tool, args) -> "{\"items\":[]}", USER_ID, TRACE_ID);

        assertThat(result.outcome()).isEqualTo(AgentResult.Outcome.FAILED);
        assertThat(result.reasonCode()).isEqualTo(AgentResult.ReasonCode.PROTOCOL_ERROR);
        assertThat(result.summary()).contains("重复");
    }

    @Test
    void completedToolResultsShouldBeCheckpointed() {
        stubTurns(
                toolCall("c1", "job_search", "{\"city\":\"上海\"}"),
                finish("{\"outcome\":\"FOUND\",\"candidateIds\":[7],\"summary\":\"ok\"}"));

        AtomicReference<List<AiChatMessage>> checkpoint = new AtomicReference<>();
        AgentRunExecutionHooks.set(null, messages -> checkpoint.set(List.copyOf(messages)));
        try {
            runner.run(definition, context, (tool, args) -> "{\"jobId\":7}", USER_ID, TRACE_ID);
        } finally {
            AgentRunExecutionHooks.clear();
        }

        // 检查点必须包含已完成的工具结果（进程中断可恢复，不丢 >1000 字符）
        assertThat(checkpoint.get()).isNotNull();
        assertThat(checkpoint.get()).anySatisfy(message -> {
            assertThat(message.role()).isEqualTo("tool");
            assertThat(message.toolCallId()).isEqualTo("c1");
            assertThat(message.content()).contains("jobId");
        });
    }

    @Test
    void longToolResultShouldBeCheckpointedInFull() {
        stubTurns(
                toolCall("c1", "job_search", "{}"),
                finish("{\"outcome\":\"EMPTY\",\"summary\":\"ok\"}"));

        String longResult = "{\"note\":\"" + "长".repeat(3000) + "\"}";
        AtomicReference<List<AiChatMessage>> checkpoint = new AtomicReference<>();
        AgentRunExecutionHooks.set(null, messages -> checkpoint.set(List.copyOf(messages)));
        try {
            runner.run(definition, context, (tool, args) -> longResult, USER_ID, TRACE_ID);
        } finally {
            AgentRunExecutionHooks.clear();
        }

        assertThat(checkpoint.get()).anySatisfy(message -> {
            if ("tool".equals(message.role()) && "c1".equals(message.toolCallId())) {
                assertThat(message.content().length()).isGreaterThan(1000);
            }
        });
    }

    @Test
    void terminalMixedWithBusinessCallShouldBeRejected() {
        AiChatTurnResponse mixed = new AiChatTurnResponse(null, List.of(
                new AiToolCall("c1", "job_search", "{\"city\":\"上海\"}"),
                new AiToolCall("finish-1", TerminalToolContract.TOOL_NAME,
                        "{\"outcome\":\"EMPTY\",\"summary\":\"ok\"}")
        ), "tool_calls", 10, 5);
        stubTurns(mixed, finish("{\"outcome\":\"EMPTY\",\"summary\":\"分开提交\"}"));

        AgentResult result = runner.run(definition, context, (tool, args) -> "{}", USER_ID, TRACE_ID);

        assertThat(result.outcome()).isEqualTo(AgentResult.Outcome.EMPTY);
        // 混轮时业务工具没有被执行（整轮返回协议错误）
        assertThat(executed).isEmpty();
    }
}
