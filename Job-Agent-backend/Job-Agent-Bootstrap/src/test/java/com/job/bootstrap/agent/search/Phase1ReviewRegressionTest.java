package com.job.bootstrap.agent.search;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.job.bootstrap.agent.executor.AgentToolExecutionResult;
import com.job.bootstrap.agent.executor.AgentToolInvoker;
import com.job.bootstrap.agent.guardrail.AgentGuardrailService;
import com.job.bootstrap.agent.plan.AgentPlanParameterExtractor;
import com.job.bootstrap.agent.react.*;
import com.job.bootstrap.agent.run.AgentRunService;
import com.job.bootstrap.agent.schema.AgentToolGuard;
import com.job.bootstrap.agent.schema.AgentToolSchemaRegistry;
import com.job.bootstrap.agent.tools.*;
import com.job.bootstrap.ai.turn.*;
import com.job.bootstrap.service.*;
import com.job.common.agent.AgentRuntimeContext;
import com.job.common.entity.agent.AgentRun;
import com.job.common.entity.position.JobPosition;
import com.job.enums.AgentRunStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.*;
import java.util.stream.Collectors;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 第一阶段验收回归测试（外部审查提供，2026-11-03）。
 *
 * 说明: 断言的是“正确行为”，在 d1f5529 上应失败；本类作为修复门禁，修复后必须全绿。
 * 覆盖: 真实工具身份桥接、任务身份/续跑、结果验真、超时、消息配对、薪资单位。
 */
class Phase1ReviewRegressionTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @AfterEach void cleanup() { AgentRuntimeContext.clear(); }

    private AiChatTurnResponse call(String id, String name, String args) {
        return new AiChatTurnResponse(null, List.of(new AiToolCall(id, name, args)), "tool_calls", 1, 1);
    }
    private AiChatTurnResponse finish(String args) {
        return call("finish", TerminalToolContract.TOOL_NAME, args);
    }
    private ReActRunner runner(AiModelGatewayService gateway) {
        return new ReActRunner(gateway, new TerminalToolContract(mapper), mapper);
    }
    private JobSearchWorkflow workflow(AiModelGatewayService gateway, AgentToolInvoker invoker) {
        return new JobSearchWorkflow(runner(gateway), new TerminalToolContract(mapper), invoker, new JobCandidatesAdapter(mapper));
    }
    private SearchConstraints constraints() {
        return SearchConstraints.merge(Map.of("keyword","Java","city","上海"), null);
    }
    private AgentToolExecutionResult realCandidate() {
        return AgentToolExecutionResult.builder().success(true).dataJson(
                "{\"total\":1,\"page\":1,\"pageSize\":5,\"jobs\":[{\"jobId\":11,\"jobTitle\":\"Java\",\"city\":\"上海\",\"minSalary\":20000,\"maxSalary\":30000}]}").build();
    }
    private JobSearchWorkflow.WorkflowInput input() {
        return new JobSearchWorkflow.WorkflowInput(100L,"review","找上海Java岗位", constraints(),null,0,0,System.currentTimeMillis());
    }

    @Test void emptyMustBeRejectedWhenAQualifiedCandidateExists() {
        AiModelGatewayService gateway = mock(AiModelGatewayService.class);
        AgentToolInvoker invoker = mock(AgentToolInvoker.class);
        when(invoker.invoke(anyString(), any(), any())).thenReturn(realCandidate());
        when(gateway.chatTurn(anyString(),any(),any(),anyLong(),anyString())).thenReturn(
                call("s1","job_search","{\"keyword\":\"Java\"}"),
                finish("{\"outcome\":\"EMPTY\",\"summary\":\"没有岗位\"}"));
        var result = workflow(gateway, invoker).execute(input());
        assertThat(result.outcome()).as("qualified pool is nonempty; EMPTY must fail validation")
                .isEqualTo(AgentResult.Outcome.FAILED);
    }

    @Test void mixedRealAndInventedIdsMustNotBeAcceptedAsVerifiedFound() {
        AiModelGatewayService gateway = mock(AiModelGatewayService.class);
        AgentToolInvoker invoker = mock(AgentToolInvoker.class);
        when(invoker.invoke(anyString(), any(), any())).thenReturn(realCandidate());
        when(gateway.chatTurn(anyString(),any(),any(),anyLong(),anyString())).thenReturn(
                call("s1","job_search","{\"keyword\":\"Java\"}"),
                finish("{\"outcome\":\"FOUND\",\"candidateIds\":[11,999],\"summary\":\"完成\"}"));
        assertThat(workflow(gateway, invoker).execute(input()).outcome())
                .as("the proposal contains an invented candidate")
                .isEqualTo(AgentResult.Outcome.FAILED);
    }

    @Test void modelSummaryMustNotIntroduceUnexecutedActions() {
        AiModelGatewayService gateway = mock(AiModelGatewayService.class);
        AgentToolInvoker invoker = mock(AgentToolInvoker.class);
        when(invoker.invoke(anyString(), any(), any())).thenReturn(realCandidate());
        when(gateway.chatTurn(anyString(),any(),any(),anyLong(),anyString())).thenReturn(
                call("s1","job_search","{\"keyword\":\"Java\"}"),
                finish("{\"outcome\":\"FOUND\",\"candidateIds\":[11],\"summary\":\"已替你投递到C公司\"}"));
        assertThat(workflow(gateway, invoker).execute(input()).answer())
                .doesNotContain("已替你投递");
    }

    @Test void searchSalaryMustBeRenderedInTheCorrectUnit() {
        AiModelGatewayService gateway = mock(AiModelGatewayService.class);
        AgentToolInvoker invoker = mock(AgentToolInvoker.class);
        when(invoker.invoke(anyString(), any(), any())).thenReturn(realCandidate());
        when(gateway.chatTurn(anyString(),any(),any(),anyLong(),anyString())).thenReturn(
                call("s1","job_search","{}"),
                finish("{\"outcome\":\"FOUND\",\"candidateIds\":[11],\"summary\":\"完成\"}"));
        assertThat(workflow(gateway, invoker).execute(input()).answer())
                .as("JobPosition salary fields are yuan/month, not K")
                .doesNotContain("20000-30000K");
    }

    @Test void disablingNewTaskSwitchMustNotDisableAnExistingRun() {
        ReactSearchProperties props = new ReactSearchProperties();
        props.setEnabled(false);
        AgentRunService runs = mock(AgentRunService.class);
        AgentRun waiting = new AgentRun();
        waiting.setId(1L);waiting.setUserId(100L);waiting.setPlanId(88L);
        waiting.setAgentName(JobSearchWorkflow.AGENT_NAME);
        waiting.setStatus(AgentRunStatus.WAITING_INPUT.name());
        when(runs.findLatestByPlan(88L)).thenReturn(waiting);
        var entry = new JobSearchReactEntry(props,runs,mock(JobSearchWorkflow.class),
                new AgentPlanParameterExtractor(),mock(UserJobPreferenceService.class),
                mock(AgentTraceService.class),mapper);
        assertThat(entry.shouldHandle("GENERAL_CHAT",88L))
                .as("existing run must retain its execution mode").isTrue();
    }

    @Test void followUpMustRetainTheOriginalHardConstraints() throws Exception {
        ReactSearchProperties props = new ReactSearchProperties();props.setEnabled(true);
        AgentRunService runs = mock(AgentRunService.class);
        JobSearchWorkflow workflow = mock(JobSearchWorkflow.class);
        AgentRun waiting = new AgentRun();
        waiting.setId(1L);waiting.setUserId(100L);waiting.setPlanId(88L);waiting.setAttempt(1);
        waiting.setAgentName(JobSearchWorkflow.AGENT_NAME);
        waiting.setStatus(AgentRunStatus.WAITING_INPUT.name());
        waiting.setConstraintsJson(mapper.writeValueAsString(SearchConstraints.merge(
                Map.of("city","上海","minSalary",20000),null)));
        waiting.setMessagesJson(mapper.writeValueAsString(List.of(
                AiChatMessage.system("搜索任务"),AiChatMessage.user("找上海、最低薪资20K的岗位"))));
        when(runs.findLatestByPlan(88L)).thenReturn(waiting);
        when(runs.claim(1L,1)).thenReturn(true);
        when(runs.finish(any(),anyInt(),any(),any(),any(),any())).thenReturn(true);
        when(workflow.execute(any())).thenReturn(new JobSearchWorkflow.WorkflowOutcome(
                AgentResult.Outcome.EMPTY,null,"空",List.of(),List.of(),null,List.of(),0,1));
        var entry = new JobSearchReactEntry(props,runs,workflow,new AgentPlanParameterExtractor(),
                mock(UserJobPreferenceService.class),mock(AgentTraceService.class),mapper);
        entry.execute(100L,10L,88L,"review","GENERAL_CHAT","Java");
        ArgumentCaptor<JobSearchWorkflow.WorkflowInput> input = ArgumentCaptor.forClass(JobSearchWorkflow.WorkflowInput.class);
        verify(workflow).execute(input.capture());
        assertThat(input.getValue().constraints().city()).isEqualTo("上海");
        assertThat(input.getValue().constraints().minSalary()).isEqualTo(20000);
    }

    @Test void realToolMustReceiveIdentityFromTheNewEntry() {
        AgentRuntimeContext.clear();
        AiModelGatewayService gateway = mock(AiModelGatewayService.class);
        JobPositionService positions = mock(JobPositionService.class);
        AgentTraceService trace = mock(AgentTraceService.class);
        JobPosition job = new JobPosition();
        job.setId(11L);job.setJobTitle("Java");job.setCity("上海");
        Page<JobPosition> page = new Page<>();page.setRecords(List.of(job));page.setTotal(1);
        when(positions.pagePositions(anyLong(),anyLong(),any(),any(),any(),any(),any(),any(),any(),any(),any(),anyBoolean()))
                .thenReturn(page);
        JobSearchTool search = new JobSearchTool(positions,mapper,trace,mock(AgentToolGuard.class));
        AgentToolInvoker invoker = new AgentToolInvoker(
                mock(ResumeAnalyzeTool.class),mock(JobMatchTool.class),mock(GreetingGenerateTool.class),
                search,mock(JobRecommendTool.class),mock(InterviewPrepareTool.class),
                mock(MockInterviewReviewTool.class),mock(RagSearchTool.class),
                new AgentToolSchemaRegistry(),mock(AgentGuardrailService.class),mock(AdminExternalConnectorService.class));
        when(gateway.chatTurn(anyString(),any(),any(),anyLong(),anyString())).thenReturn(
                call("s1","job_search","{\"keyword\":\"Java\"}"),
                finish("{\"outcome\":\"FOUND\",\"candidateIds\":[11],\"summary\":\"完成\"}"));
        AgentRunService runs = mock(AgentRunService.class);
        AgentRun run = new AgentRun();run.setId(1L);run.setUserId(100L);run.setAttempt(0);
        when(runs.create(any(),any(),any(),any(),any(),any(),any())).thenReturn(run);
        when(runs.claim(1L,0)).thenReturn(true);
        when(runs.finish(any(),anyInt(),any(),any(),any(),any())).thenReturn(true);
        ReactSearchProperties props = new ReactSearchProperties();props.setEnabled(true);
        var entry = new JobSearchReactEntry(props,runs,workflow(gateway,invoker),
                new AgentPlanParameterExtractor(),mock(UserJobPreferenceService.class),trace,mapper);
        var result = entry.execute(100L,10L,null,"review","JOB_SEARCH","找上海Java岗位");
        assertThat(result.outcome().outcome()).as("new entry -> real invoker -> real search tool")
                .isEqualTo(AgentResult.Outcome.FOUND);
    }

    @Test void aLateModelResultMustNotBeAcceptedAfterDeadline() {
        AiModelGatewayService gateway = mock(AiModelGatewayService.class);
        when(gateway.chatTurn(anyString(),any(),any(),anyLong(),anyString())).thenAnswer(invocation -> {
            Thread.sleep(1100);
            return finish("{\"outcome\":\"EMPTY\",\"summary\":\"没有岗位\"}");
        });
        AgentDefinition definition = new AgentDefinition("job-search","system",
                Map.of("job_search",JobSearchWorkflow.SEARCH_TOOL),List.of(),
                TerminalToolContract.TOOL_NAME,6,8,1);
        var result = runner(gateway).run(definition,new AgentRunContext("system","query"),(tool,args)->"{}",100L,"review");
        assertThat(result.outcome()).as("deadline is 1 second, result arrives after 1.1 seconds")
                .isEqualTo(AgentResult.Outcome.FAILED);
        assertThat(result.reasonCode()).isEqualTo("BUDGET_TIMEOUT");
    }

    @Test void earlyFailureMustStillCloseAllCallsInTheAssistantMessage() {
        AiModelGatewayService gateway = mock(AiModelGatewayService.class);
        when(gateway.chatTurn(anyString(),any(),any(),anyLong(),anyString())).thenReturn(
                call("c1","job_search","{\"page\":1}"),
                new AiChatTurnResponse(null,List.of(
                        new AiToolCall("c2","job_search","{\"page\":1}"),
                        new AiToolCall("c3","job_search","{\"page\":2}")),"tool_calls",1,1));
        AgentDefinition definition = new AgentDefinition("job-search","system",
                Map.of("job_search",JobSearchWorkflow.SEARCH_TOOL),List.of(),
                TerminalToolContract.TOOL_NAME,6,8,180);
        AgentRunContext context = new AgentRunContext("system","query");
        runner(gateway).run(definition,context,(tool,args)->"{}",100L,"review");
        Set<String> requested=context.messages().stream().filter(m->m.toolCalls()!=null)
                .flatMap(m->m.toolCalls().stream()).map(AiToolCall::id).collect(Collectors.toSet());
        Set<String> answered=context.messages().stream().filter(m->"tool".equals(m.role()))
                .map(AiChatMessage::toolCallId).collect(Collectors.toSet());
        requested.removeAll(answered);
        assertThat(requested).as("all tool_call IDs must have paired tool messages").isEmpty();
    }

    @Test void targetRoleMustNotBeChangedByModelSearchArguments() {
        AiModelGatewayService gateway = mock(AiModelGatewayService.class);
        AgentToolInvoker invoker = mock(AgentToolInvoker.class);
        when(invoker.invoke(anyString(), any(), any())).thenReturn(
                AgentToolExecutionResult.builder().success(true)
                        .dataJson("{\"total\":1,\"jobs\":[{\"jobId\":11,\"jobTitle\":\"Python开发\",\"city\":\"上海\",\"skillKeywords\":\"Python\"}]}").build());
        when(gateway.chatTurn(anyString(),any(),any(),anyLong(),anyString())).thenReturn(
                call("s1","job_search","{\"keyword\":\"Python\"}"),
                finish("{\"outcome\":\"FOUND\",\"candidateIds\":[11],\"summary\":\"完成\"}"));
        assertThat(workflow(gateway, invoker).execute(input()).outcome())
                .as("user requested Java but model searched Python")
                .isEqualTo(AgentResult.Outcome.FAILED);
    }

    @Test void anExistingRunningTaskMustNotCreateAnotherRun() {
        ReactSearchProperties props = new ReactSearchProperties();props.setEnabled(true);
        AgentRunService runs = mock(AgentRunService.class);
        JobSearchWorkflow wf = mock(JobSearchWorkflow.class);
        AgentRun existing = new AgentRun();
        existing.setId(1L);existing.setUserId(100L);existing.setPlanId(88L);existing.setAttempt(1);
        existing.setAgentName(JobSearchWorkflow.AGENT_NAME);
        existing.setStatus(AgentRunStatus.RUNNING.name());
        AgentRun created = new AgentRun();created.setId(2L);created.setAttempt(0);
        when(runs.findLatestByPlan(88L)).thenReturn(existing);
        when(runs.create(any(),any(),any(),any(),any(),any(),any())).thenReturn(created);
        when(runs.claim(2L,0)).thenReturn(true);
        when(runs.finish(any(),anyInt(),any(),any(),any(),any())).thenReturn(true);
        when(wf.execute(any())).thenReturn(new JobSearchWorkflow.WorkflowOutcome(
                AgentResult.Outcome.EMPTY,null,"空",List.of(),List.of(),null,List.of(),0,1));
        var entry = new JobSearchReactEntry(props,runs,wf,new AgentPlanParameterExtractor(),
                mock(UserJobPreferenceService.class),mock(AgentTraceService.class),mapper);
        entry.execute(100L,10L,88L,"review","JOB_SEARCH","找上海Java");
        verify(runs,never()).create(any(),any(),any(),any(),any(),any(),any());
    }

}
