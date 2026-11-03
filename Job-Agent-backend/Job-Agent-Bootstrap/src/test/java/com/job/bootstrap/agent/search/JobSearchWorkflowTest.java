package com.job.bootstrap.agent.search;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.job.bootstrap.agent.executor.AgentToolExecutionResult;
import com.job.bootstrap.agent.executor.AgentToolInvoker;
import com.job.bootstrap.agent.react.AgentResult;
import com.job.bootstrap.agent.react.ReActRunner;
import com.job.bootstrap.agent.react.TerminalToolContract;
import com.job.bootstrap.ai.turn.AiChatTurnResponse;
import com.job.bootstrap.ai.turn.AiChatTurnRequest;
import com.job.bootstrap.ai.turn.AiToolCall;
import com.job.bootstrap.service.AiModelGatewayService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * JobSearchWorkflow 集成测试（第一阶段）。
 *
 * 覆盖重点（对应文档测试清单 3/4/5）:
 * 1. FOUND：候选必须来自真实工具结果，对外答复由服务端用真实字段重建；
 * 2. 伪造候选 ID、硬条件不符的候选 → 整单 FAILED（不采用模型提议）；
 * 3. EMPTY 红线：未实际查询成功就声明 EMPTY → FAILED；
 * 4. 硬条件不可篡改：模型在参数里传城市，执行器用约束快照覆盖；
 * 5. NEED_INPUT 透传（问题 + 缺失字段）。
 */
class JobSearchWorkflowTest {

    private static final Long USER_ID = 100L;
    private static final String TRACE_ID = "trace-workflow";

    private final AiModelGatewayService gateway = mock(AiModelGatewayService.class);
    private final AgentToolInvoker invoker = mock(AgentToolInvoker.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final TerminalToolContract terminal = new TerminalToolContract(objectMapper);
    private final ReActRunner runner = new ReActRunner(gateway, terminal, objectMapper);
    private final JobCandidatesAdapter adapter = new JobCandidatesAdapter(objectMapper);
    private final JobSearchWorkflow workflow = new JobSearchWorkflow(runner, terminal, invoker, adapter);

    private SearchConstraints constraints;

    @BeforeEach
    void setUp() {
        constraints = SearchConstraints.merge(
                Map.of("keyword", "Java", "city", "上海", "minSalary", 25),
                null
        );
    }

    private void stubTurns(AiChatTurnResponse... responses) {
        var stubbing = when(gateway.chatTurn(anyString(), any(), any(AiChatTurnRequest.class), anyLong(), anyString()));
        for (int i = 0; i < responses.length - 1; i++) {
            stubbing = stubbing.thenReturn(responses[i]);
        }
        stubbing.thenReturn(responses[responses.length - 1]);
    }

    private AiChatTurnResponse search(String args) {
        return new AiChatTurnResponse(null, List.of(new AiToolCall("c1", "job_search", args)), "tool_calls", 10, 5);
    }

    private AiChatTurnResponse finish(String args) {
        return new AiChatTurnResponse(null, List.of(new AiToolCall("f1", TerminalToolContract.TOOL_NAME, args)), "tool_calls", 10, 5);
    }

    private void stubSearchResult(String dataJson) {
        when(invoker.invoke(eq(JobSearchWorkflow.SEARCH_TOOL), any(), any()))
                .thenReturn(AgentToolExecutionResult.builder()
                        .success(true).toolName(JobSearchWorkflow.SEARCH_TOOL)
                        .message("ok").dataJson(dataJson).costTime(1L).build());
    }

    private JobSearchWorkflow.WorkflowOutcome run(String message) {
        return workflow.execute(new JobSearchWorkflow.WorkflowInput(
                USER_ID, TRACE_ID, message, constraints, null, 0, 0, System.currentTimeMillis()));
    }

    @Test
    void foundShouldRebuildAnswerFromRealCandidates() {
        stubSearchResult("""
                {"total":2,"page":1,"pageSize":5,"jobs":[
                  {"jobId":11,"jobTitle":"Java 后端开发","companyName":"A公司","city":"上海","minSalary":20,"maxSalary":35},
                  {"jobId":12,"jobTitle":"Java 架构师","companyName":"B公司","city":"北京","minSalary":30,"maxSalary":50}
                ]}
                """);
        stubTurns(search("{\"keyword\":\"Java\"}"),
                finish("{\"outcome\":\"FOUND\",\"candidateIds\":[11],\"summary\":\"按你的条件找到 1 个\"}"));

        JobSearchWorkflow.WorkflowOutcome outcome = run("找上海 Java 岗位，25K 以上");

        assertThat(outcome.outcome()).isEqualTo(AgentResult.Outcome.FOUND);
        assertThat(outcome.candidates()).hasSize(1);
        // 服务端重建答复：真实字段（公司/城市/薪资），不采用模型编造内容
        assertThat(outcome.answer()).contains("Java 后端开发").contains("A公司").contains("上海").contains("20-35K");
    }

    @Test
    void fabricatedCandidateShouldFail() {
        stubSearchResult("""
                {"total":1,"page":1,"pageSize":5,"jobs":[
                  {"jobId":11,"jobTitle":"Java 后端开发","companyName":"A公司","city":"上海","minSalary":20,"maxSalary":35}
                ]}
                """);
        stubTurns(search("{}"), finish("{\"outcome\":\"FOUND\",\"candidateIds\":[999],\"summary\":\"ok\"}"));

        JobSearchWorkflow.WorkflowOutcome outcome = run("找上海 Java 岗位");

        assertThat(outcome.outcome()).isEqualTo(AgentResult.Outcome.FAILED);
        assertThat(outcome.reasonCode()).isEqualTo("PROPOSAL_VERIFICATION_FAILED");
        assertThat(outcome.candidates()).isEmpty();
    }

    @Test
    void hardConditionViolationShouldFail() {
        // 工具返回了北京岗位（模拟数据异常），候选虽真实但不满足城市硬条件
        stubSearchResult("""
                {"total":1,"page":1,"pageSize":5,"jobs":[
                  {"jobId":12,"jobTitle":"Java 架构师","companyName":"B公司","city":"北京","minSalary":30,"maxSalary":50}
                ]}
                """);
        stubTurns(search("{}"), finish("{\"outcome\":\"FOUND\",\"candidateIds\":[12],\"summary\":\"ok\"}"));

        JobSearchWorkflow.WorkflowOutcome outcome = run("找上海 Java 岗位");

        assertThat(outcome.outcome()).isEqualTo(AgentResult.Outcome.FAILED);
        assertThat(outcome.reasonCode()).isEqualTo("PROPOSAL_VERIFICATION_FAILED");
    }

    @Test
    void emptyWithoutAnySuccessfulSearchShouldFail() {
        stubTurns(finish("{\"outcome\":\"EMPTY\",\"summary\":\"没有岗位\"}"));

        JobSearchWorkflow.WorkflowOutcome outcome = run("找火星上的岗位");

        assertThat(outcome.outcome()).isEqualTo(AgentResult.Outcome.FAILED);
        assertThat(outcome.reasonCode()).isEqualTo("EMPTY_WITHOUT_SEARCH");
    }

    @Test
    void emptyAfterSuccessfulSearchShouldBeEmpty() {
        stubSearchResult("{\"total\":0,\"page\":1,\"pageSize\":5,\"jobs\":[]}");
        stubTurns(search("{}"), finish("{\"outcome\":\"EMPTY\",\"summary\":\"查询了上海 Java，暂无结果\"}"));

        JobSearchWorkflow.WorkflowOutcome outcome = run("找上海 Java 岗位");

        assertThat(outcome.outcome()).isEqualTo(AgentResult.Outcome.EMPTY);
        assertThat(outcome.answer()).contains("没有找到");
    }

    @Test
    void modelCityShouldBeOverriddenByConstraint() {
        stubSearchResult("{\"total\":0,\"page\":1,\"pageSize\":5,\"jobs\":[]}");
        // 模型试图把城市改成北京
        stubTurns(search("{\"keyword\":\"Java\",\"city\":\"北京\"}"),
                finish("{\"outcome\":\"EMPTY\",\"summary\":\"ok\"}"));

        run("找上海 Java 岗位");

        // 执行器必须用约束快照的上海覆盖模型给的城市
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(invoker).invoke(eq(JobSearchWorkflow.SEARCH_TOOL), captor.capture(), any());
        assertThat(captor.getValue().get("city")).isEqualTo("上海");
    }

    @Test
    void needInputShouldPassThrough() {
        stubTurns(finish("{\"outcome\":\"NEED_INPUT\",\"missingFields\":[\"city\"],\"question\":\"你希望在哪个城市工作？\",\"summary\":\"缺城市\"}"));

        JobSearchWorkflow.WorkflowOutcome outcome = run("帮我找岗位");

        assertThat(outcome.outcome()).isEqualTo(AgentResult.Outcome.NEED_INPUT);
        assertThat(outcome.missingFields()).containsExactly("city");
        assertThat(outcome.question()).contains("城市");
    }
}
