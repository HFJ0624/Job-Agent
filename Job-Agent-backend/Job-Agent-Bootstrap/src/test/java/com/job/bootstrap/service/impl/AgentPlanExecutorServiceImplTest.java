package com.job.bootstrap.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.job.bootstrap.agent.context.AgentRuntimeContext;
import com.job.bootstrap.agent.executor.AgentPlanExecutionResult;
import com.job.bootstrap.agent.executor.AgentToolExecutionResult;
import com.job.bootstrap.agent.executor.AgentToolInvoker;
import com.job.bootstrap.agent.guardrail.AgentGuardrailService;
import com.job.bootstrap.agent.plan.AgentReplanner;
import com.job.bootstrap.agent.schema.AgentToolSchemaRegistry;
import com.job.bootstrap.mapper.AgentPlanMapper;
import com.job.bootstrap.mapper.AgentPlanStepMapper;
import com.job.bootstrap.observability.AgentObservationRecord;
import com.job.bootstrap.service.AgentMemoryExtractionService;
import com.job.bootstrap.service.AgentObservationService;
import com.job.common.agent.tool.AgentToolSchema;
import com.job.common.entity.agent.AgentPlan;
import com.job.common.entity.agent.AgentPlanStep;
import com.job.enums.AgentPlanStatus;
import com.job.enums.AgentPlanStepStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.mockito.ArgumentCaptor;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Agent 计划执行器测试。
 *
 * 覆盖重点:
 * 1. 计划级幂等：已完成/已失败的计划不会重复执行。
 * 2. 执行抢占：RUNNING 计划在心跳超时内拒绝并发重复提交；超时残留允许重新抢占。
 * 3. 步骤级幂等：失败恢复时已完成的步骤采用历史结果，副作用工具不会重复调用。
 * 4. 抢占竞争：条件更新影响行数为 0 时不执行任何工具。
 * 5. 失败语义：任一步骤失败则终止后续步骤，计划置为 FAILED。
 * 6. Replan（任务 G2）：步骤失败且有预算时，Replanner 给出替代步骤、失败步骤放弃、追加步骤继续执行；
 *    预算用尽或无建议时按原失败语义终止。
 */
class AgentPlanExecutorServiceImplTest {

    private static final Long USER_ID = 100L;
    private static final Long PLAN_ID = 1L;

    private AgentPlanMapper agentPlanMapper;
    private AgentPlanStepMapper agentPlanStepMapper;
    private AgentToolInvoker agentToolInvoker;
    private AgentMemoryExtractionService agentMemoryExtractionService;
    private AgentToolSchemaRegistry agentToolSchemaRegistry;
    private AgentGuardrailService agentGuardrailService;
    private AgentObservationService agentObservationService;
    private AgentReplanner agentReplanner;

    private AgentPlanExecutorServiceImpl executor;

    @BeforeEach
    void setUp() {
        agentPlanMapper = mock(AgentPlanMapper.class);
        agentPlanStepMapper = mock(AgentPlanStepMapper.class);
        agentToolInvoker = mock(AgentToolInvoker.class);
        agentMemoryExtractionService = mock(AgentMemoryExtractionService.class);
        agentToolSchemaRegistry = mock(AgentToolSchemaRegistry.class);
        agentGuardrailService = mock(AgentGuardrailService.class);
        agentObservationService = mock(AgentObservationService.class);
        agentReplanner = mock(AgentReplanner.class);

        executor = new AgentPlanExecutorServiceImpl(
                agentPlanMapper,
                agentPlanStepMapper,
                agentToolInvoker,
                agentMemoryExtractionService,
                agentToolSchemaRegistry,
                agentGuardrailService,
                agentObservationService,
                agentReplanner,
                new ObjectMapper()
        );

        /*
         * 工具名解析默认原样返回，Schema 查询默认返回一个 Mock，
         * 保证测试只关注执行编排逻辑，不依赖具体工具定义。
         */
        when(agentToolSchemaRegistry.resolveToolName(any()))
                .thenAnswer(invocation -> Optional.of(invocation.getArgument(0)));
        when(agentToolSchemaRegistry.getRequired(any())).thenReturn(mock(AgentToolSchema.class));

        /*
         * 真实链路里 AgentChatServiceImpl 会在执行计划前设置运行时上下文，
         * 工具执行时通过 ThreadLocal 取 userId/traceId。
         * 单测需要模拟这一环境，否则 executeToolStep 会因为缺少上下文而失败。
         */
        AgentRuntimeContext.set(USER_ID, 10L, "trace-test-0001", "JOB_MATCH", List.of());
    }

    @AfterEach
    void tearDown() {
        AgentRuntimeContext.clear();
    }

    /**
     * 用例1：已完成的计划直接返回历史结果，不重复调用工具。
     */
    @Test
    void completedPlanShouldNotReexecuteTools() {
        AgentPlan plan = buildPlan(AgentPlanStatus.COMPLETED.name(), new Date());
        List<AgentPlanStep> steps = List.of(
                buildStep(1, "ResumeAnalyzeTool.analyzeResume", AgentPlanStepStatus.COMPLETED.name())
        );
        when(agentPlanMapper.selectById(PLAN_ID)).thenReturn(plan);
        when(agentPlanStepMapper.selectList(any())).thenReturn(steps);

        AgentPlanExecutionResult result = executor.executePlan(USER_ID, PLAN_ID);

        assertThat(result.getSuccess()).isTrue();
        assertThat(result.getMessage()).contains("已执行完成");
        verify(agentToolInvoker, never()).invoke(any(), any(), any());
        verify(agentPlanMapper, never()).update(any(), any());
    }

    /**
     * 用例2：失败计划在默认模式下不重跑，保持“请重新发起”语义。
     */
    @Test
    void failedPlanShouldNotReexecuteWithoutRecover() {
        AgentPlan plan = buildPlan(AgentPlanStatus.FAILED.name(), new Date());
        List<AgentPlanStep> steps = List.of(
                buildStep(1, "ResumeAnalyzeTool.analyzeResume", AgentPlanStepStatus.FAILED.name())
        );
        when(agentPlanMapper.selectById(PLAN_ID)).thenReturn(plan);
        when(agentPlanStepMapper.selectList(any())).thenReturn(steps);

        AgentPlanExecutionResult result = executor.executePlan(USER_ID, PLAN_ID, false);

        assertThat(result.getSuccess()).isFalse();
        assertThat(result.getMessage()).contains("请重新发起");
        verify(agentToolInvoker, never()).invoke(any(), any(), any());
        verify(agentPlanMapper, never()).update(any(), any());
    }

    /**
     * 用例3：失败计划恢复时，已完成的步骤采用历史结果，只重跑未完成步骤。
     *
     * 这是步骤级幂等的核心场景：如果恢复时重跑步骤1，
     * GreetingGenerateTool 这类副作用工具会重复创建沟通记录。
     */
    @Test
    void failedPlanWithRecoverShouldOnlyRunIncompleteSteps() {
        AgentPlan plan = buildPlan(AgentPlanStatus.FAILED.name(), new Date());
        AgentPlanStep completedStep = buildStep(
                1, "ResumeAnalyzeTool.analyzeResume", AgentPlanStepStatus.COMPLETED.name());
        completedStep.setResultSummary("{\"totalScore\":86}");
        AgentPlanStep failedStep = buildStep(
                2, "JobMatchTool.matchJob", AgentPlanStepStatus.FAILED.name());
        failedStep.setErrorMsg("岗位不存在");
        List<AgentPlanStep> steps = List.of(completedStep, failedStep);

        when(agentPlanMapper.selectById(PLAN_ID)).thenReturn(plan);
        when(agentPlanStepMapper.selectList(any())).thenReturn(steps);
        when(agentPlanMapper.update(any(), any())).thenReturn(1);
        when(agentToolInvoker.invoke(any(), any(), any())).thenReturn(buildToolSuccess("JobMatchTool.matchJob"));

        AgentPlanExecutionResult result = executor.executePlan(USER_ID, PLAN_ID, true);

        assertThat(result.getSuccess()).isTrue();
        assertThat(result.getStatus()).isEqualTo(AgentPlanStatus.COMPLETED.name());

        // 步骤1采用历史结果，步骤2真实重跑
        verify(agentToolInvoker, times(1)).invoke(any(), any(), any());
        verify(agentToolInvoker, never()).invoke(
                eq("ResumeAnalyzeTool.analyzeResume"), any(), any());
        assertThat(result.getSteps()).hasSize(2);
        assertThat(result.getSteps().get(0).getIdempotentHit()).isTrue();
        assertThat(result.getSteps().get(1).getIdempotentHit()).isNull();
    }

    /**
     * 用例4：RUNNING 计划在心跳超时内拒绝并发重复提交。
     */
    @Test
    void runningPlanShouldRejectConcurrentSubmit() {
        AgentPlan plan = buildPlan(AgentPlanStatus.RUNNING.name(), new Date());
        List<AgentPlanStep> steps = List.of(
                buildStep(1, "ResumeAnalyzeTool.analyzeResume", AgentPlanStepStatus.RUNNING.name())
        );
        when(agentPlanMapper.selectById(PLAN_ID)).thenReturn(plan);
        when(agentPlanStepMapper.selectList(any())).thenReturn(steps);

        AgentPlanExecutionResult result = executor.executePlan(USER_ID, PLAN_ID);

        assertThat(result.getSuccess()).isFalse();
        assertThat(result.getMessage()).contains("正在执行中");
        verify(agentToolInvoker, never()).invoke(any(), any(), any());
        verify(agentPlanMapper, never()).update(any(), any());
    }

    /**
     * 用例5：RUNNING 计划心跳超时判定为崩溃残留，允许重新抢占执行。
     */
    @Test
    void staleRunningPlanShouldBeReclaimed() {
        Date staleTime = new Date(System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(30));
        AgentPlan plan = buildPlan(AgentPlanStatus.RUNNING.name(), staleTime);
        List<AgentPlanStep> steps = List.of(
                buildStep(1, "ResumeAnalyzeTool.analyzeResume", AgentPlanStepStatus.RUNNING.name())
        );
        when(agentPlanMapper.selectById(PLAN_ID)).thenReturn(plan);
        when(agentPlanStepMapper.selectList(any())).thenReturn(steps);
        when(agentPlanMapper.update(any(), any())).thenReturn(1);
        when(agentToolInvoker.invoke(any(), any(), any()))
                .thenReturn(buildToolSuccess("ResumeAnalyzeTool.analyzeResume"));

        AgentPlanExecutionResult result = executor.executePlan(USER_ID, PLAN_ID, false);

        assertThat(result.getSuccess()).isTrue();
        verify(agentToolInvoker, times(1)).invoke(any(), any(), any());
    }

    /**
     * 用例6：抢占竞争失败（条件更新影响 0 行）时不执行任何工具，
     * 并重新读取计划最新状态返回，避免把过期状态告诉用户。
     */
    @Test
    void claimRaceLostShouldNotExecute() {
        AgentPlan plannedPlan = buildPlan(AgentPlanStatus.PLANNED.name(), new Date());
        AgentPlan completedPlan = buildPlan(AgentPlanStatus.COMPLETED.name(), new Date());
        List<AgentPlanStep> steps = List.of(
                buildStep(1, "ResumeAnalyzeTool.analyzeResume", AgentPlanStepStatus.PENDING.name())
        );
        when(agentPlanMapper.selectById(PLAN_ID)).thenReturn(plannedPlan, completedPlan);
        when(agentPlanStepMapper.selectList(any())).thenReturn(steps);
        when(agentPlanMapper.update(any(), any())).thenReturn(0);

        AgentPlanExecutionResult result = executor.executePlan(USER_ID, PLAN_ID);

        assertThat(result.getSuccess()).isTrue();
        assertThat(result.getMessage()).contains("已执行完成");
        verify(agentToolInvoker, never()).invoke(any(), any(), any());
    }

    /**
     * 用例7：任一步骤失败则终止后续步骤，计划置为 FAILED。
     */
    @Test
    void failedStepShouldSkipSubsequentSteps() {
        AgentPlan plan = buildPlan(AgentPlanStatus.PLANNED.name(), new Date());
        List<AgentPlanStep> steps = List.of(
                buildStep(1, "ResumeAnalyzeTool.analyzeResume", AgentPlanStepStatus.PENDING.name()),
                buildStep(2, "JobMatchTool.matchJob", AgentPlanStepStatus.PENDING.name())
        );
        when(agentPlanMapper.selectById(PLAN_ID)).thenReturn(plan);
        when(agentPlanStepMapper.selectList(any())).thenReturn(steps);
        when(agentPlanMapper.update(any(), any())).thenReturn(1);
        when(agentToolInvoker.invoke(any(), any(), any())).thenReturn(AgentToolExecutionResult.builder()
                .success(false)
                .toolName("ResumeAnalyzeTool.analyzeResume")
                .errorCode("TOOL_EXECUTION_FAILED")
                .message("简历解析失败")
                .costTime(1L)
                .build());

        AgentPlanExecutionResult result = executor.executePlan(USER_ID, PLAN_ID);

        assertThat(result.getSuccess()).isFalse();
        assertThat(result.getStatus()).isEqualTo(AgentPlanStatus.FAILED.name());
        assertThat(result.getSteps().get(0).getStatus()).isEqualTo(AgentPlanStepStatus.FAILED.name());
        assertThat(result.getSteps().get(1).getStatus()).isEqualTo(AgentPlanStepStatus.SKIPPED.name());
        verify(agentToolInvoker, times(1)).invoke(any(), any(), any());
    }

    /**
     * 用例8：没有工具名的规划/校验类步骤标记为 SKIPPED，不影响后续工具步骤。
     */
    @Test
    void stepWithoutToolShouldBeSkipped() {
        AgentPlan plan = buildPlan(AgentPlanStatus.PLANNED.name(), new Date());
        List<AgentPlanStep> steps = List.of(
                buildStep(1, null, AgentPlanStepStatus.PENDING.name()),
                buildStep(2, "ResumeAnalyzeTool.analyzeResume", AgentPlanStepStatus.PENDING.name())
        );
        when(agentPlanMapper.selectById(PLAN_ID)).thenReturn(plan);
        when(agentPlanStepMapper.selectList(any())).thenReturn(steps);
        when(agentPlanMapper.update(any(), any())).thenReturn(1);
        when(agentToolInvoker.invoke(any(), any(), any()))
                .thenReturn(buildToolSuccess("ResumeAnalyzeTool.analyzeResume"));

        AgentPlanExecutionResult result = executor.executePlan(USER_ID, PLAN_ID);

        assertThat(result.getSuccess()).isTrue();
        assertThat(result.getSteps().get(0).getStatus()).isEqualTo(AgentPlanStepStatus.SKIPPED.name());
        verify(agentToolInvoker, times(1)).invoke(any(), any(), any());
    }

    /**
     * 用例：步骤间数据传递。步骤2 的入参用 {{step_1.output.jobId}} 引用步骤1 输出，
     * Executor 应在调用工具前把占位符解析成步骤1 的真实输出值。
     */
    @Test
    void shouldResolveStepOutputPlaceholderBeforeInvoke() {
        AgentPlan plan = buildPlan(AgentPlanStatus.PLANNED.name(), new Date());
        AgentPlanStep step1 = buildStep(1, "JobSearchTool.searchJobs", AgentPlanStepStatus.PENDING.name());
        AgentPlanStep step2 = buildStep(2, "JobMatchTool.matchJob", AgentPlanStepStatus.PENDING.name());
        // 步骤2 自带可执行参数：resumeId 固定，jobId 用占位符引用步骤1 输出
        step2.setToolInputSchema("{\"extractedParams\":{\"resumeId\":7,\"jobId\":\"{{step_1.output.jobId}}\"}}");
        List<AgentPlanStep> steps = List.of(step1, step2);

        when(agentPlanMapper.selectById(PLAN_ID)).thenReturn(plan);
        when(agentPlanStepMapper.selectList(any())).thenReturn(steps);
        when(agentPlanMapper.update(any(), any())).thenReturn(1);
        // 步骤1 输出含顶层 jobId=42
        when(agentToolInvoker.invoke(eq("JobSearchTool.searchJobs"), any(), any())).thenReturn(
                AgentToolExecutionResult.builder().success(true).toolName("JobSearchTool.searchJobs")
                        .message("ok").dataJson("{\"jobId\":42,\"total\":1}").costTime(1L).build());
        when(agentToolInvoker.invoke(eq("JobMatchTool.matchJob"), any(), any()))
                .thenReturn(buildToolSuccess("JobMatchTool.matchJob"));

        executor.executePlan(USER_ID, PLAN_ID);

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(agentToolInvoker).invoke(eq("JobMatchTool.matchJob"), captor.capture(), any());
        // 占位符已被替换为步骤1 输出的 jobId；resumeId 用步骤2 自带值
        assertThat(captor.getValue().get("jobId")).isEqualTo(42);
        assertThat(captor.getValue().get("resumeId")).isEqualTo(7);
    }

    /**
     * 用例9（任务 G2）：步骤失败且有预算时触发 Replan——失败步骤放弃（SKIPPED），
     * 替代步骤追加到计划尾部继续执行，计划最终 COMPLETED。
     */
    @Test
    void failedStepShouldTriggerReplanAndComplete() {
        AgentPlan plan = buildPlan(AgentPlanStatus.PLANNED.name(), new Date());
        List<AgentPlanStep> steps = List.of(
                buildStep(1, "ResumeAnalyzeTool.analyzeResume", AgentPlanStepStatus.PENDING.name())
        );
        when(agentPlanMapper.selectById(PLAN_ID)).thenReturn(plan);
        when(agentPlanStepMapper.selectList(any())).thenReturn(steps);
        when(agentPlanMapper.update(any(), any())).thenReturn(1);
        // 第 1 次调用（原步骤）失败；第 2 次调用（替代步骤）成功
        when(agentToolInvoker.invoke(any(), any(), any()))
                .thenReturn(AgentToolExecutionResult.builder()
                        .success(false)
                        .toolName("ResumeAnalyzeTool.analyzeResume")
                        .errorCode("TOOL_EXECUTION_FAILED")
                        .message("简历解析失败")
                        .costTime(1L)
                        .build(),
                        buildToolSuccess("JobSearchTool.searchJobs"));
        when(agentReplanner.replan(any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of(new AgentReplanner.ReplanStep(
                        "替代搜索", "重新搜索岗位", "JobSearchTool.searchJobs",
                        Map.of("keyword", "Java"), "返回岗位列表")));

        AgentPlanExecutionResult result = executor.executePlan(USER_ID, PLAN_ID);

        assertThat(result.getSuccess()).isTrue();
        assertThat(result.getStatus()).isEqualTo(AgentPlanStatus.COMPLETED.name());
        assertThat(result.getSteps()).hasSize(2); // 原步骤 + 替代步骤
        verify(agentToolInvoker, times(2)).invoke(any(), any(), any());
        verify(agentPlanStepMapper).insert(any(AgentPlanStep.class)); // 替代步骤落库
        // 失败步骤最终被标记为 SKIPPED（放弃），替代步骤执行成功
        ArgumentCaptor<AgentPlanStep> stepCaptor = ArgumentCaptor.forClass(AgentPlanStep.class);
        verify(agentPlanStepMapper, atLeastOnce()).updateById(stepCaptor.capture());
        assertThat(stepCaptor.getAllValues()).anySatisfy(step -> {
            assertThat(step.getStatus()).isEqualTo(AgentPlanStepStatus.SKIPPED.name());
            assertThat(step.getResultSummary()).contains("Replan");
        });
        // 有重规划观测事件，后台可统计“失败→改道”
        verify(agentObservationService, atLeastOnce()).recordEvent(
                argThat(event -> "PlanReplan".equals(event.getEventName())));
    }

    /**
     * 用例10（任务 G2）：replan_count 已达上限时不再重规划，按原失败语义终止。
     */
    @Test
    void replanBudgetExhaustedShouldFail() {
        AgentPlan plan = buildPlan(AgentPlanStatus.PLANNED.name(), new Date());
        plan.setReplanCount(2); // 已达 MAX_REPLAN_PER_PLAN
        List<AgentPlanStep> steps = List.of(
                buildStep(1, "ResumeAnalyzeTool.analyzeResume", AgentPlanStepStatus.PENDING.name())
        );
        when(agentPlanMapper.selectById(PLAN_ID)).thenReturn(plan);
        when(agentPlanStepMapper.selectList(any())).thenReturn(steps);
        when(agentPlanMapper.update(any(), any())).thenReturn(1);
        when(agentToolInvoker.invoke(any(), any(), any())).thenReturn(AgentToolExecutionResult.builder()
                .success(false)
                .toolName("ResumeAnalyzeTool.analyzeResume")
                .errorCode("TOOL_EXECUTION_FAILED")
                .message("简历解析失败")
                .costTime(1L)
                .build());

        AgentPlanExecutionResult result = executor.executePlan(USER_ID, PLAN_ID);

        assertThat(result.getSuccess()).isFalse();
        assertThat(result.getStatus()).isEqualTo(AgentPlanStatus.FAILED.name());
        verify(agentReplanner, never()).replan(any(), any(), any(), any(), any(), any());
        verify(agentPlanStepMapper, never()).insert(any(AgentPlanStep.class));
    }

    /**
     * 用例11（任务 G2）：Replanner 无可行替代建议时，按原失败语义终止。
     */
    @Test
    void noReplanSuggestionShouldFail() {
        AgentPlan plan = buildPlan(AgentPlanStatus.PLANNED.name(), new Date());
        List<AgentPlanStep> steps = List.of(
                buildStep(1, "ResumeAnalyzeTool.analyzeResume", AgentPlanStepStatus.PENDING.name())
        );
        when(agentPlanMapper.selectById(PLAN_ID)).thenReturn(plan);
        when(agentPlanStepMapper.selectList(any())).thenReturn(steps);
        when(agentPlanMapper.update(any(), any())).thenReturn(1);
        when(agentToolInvoker.invoke(any(), any(), any())).thenReturn(AgentToolExecutionResult.builder()
                .success(false)
                .toolName("ResumeAnalyzeTool.analyzeResume")
                .errorCode("TOOL_EXECUTION_FAILED")
                .message("简历解析失败")
                .costTime(1L)
                .build());
        when(agentReplanner.replan(any(), any(), any(), any(), any(), any())).thenReturn(List.of());

        AgentPlanExecutionResult result = executor.executePlan(USER_ID, PLAN_ID);

        assertThat(result.getSuccess()).isFalse();
        verify(agentReplanner, times(1)).replan(any(), any(), any(), any(), any(), any());
        verify(agentPlanStepMapper, never()).insert(any(AgentPlanStep.class));
    }

    private AgentToolExecutionResult buildToolSuccess(String toolName) {
        return AgentToolExecutionResult.builder()
                .success(true)
                .toolName(toolName)
                .message("工具执行成功")
                .dataJson("{\"totalScore\":86}")
                .costTime(1L)
                .build();
    }

    private AgentPlan buildPlan(String status, Date updateTime) {
        AgentPlan plan = new AgentPlan();
        plan.setId(PLAN_ID);
        plan.setUserId(USER_ID);
        plan.setConversationId(10L);
        plan.setTraceId("trace-test-0001");
        plan.setIntentCode("JOB_MATCH");
        plan.setUserGoal("帮我看下简历和岗位是否匹配");
        plan.setExtractedParamsJson("{\"resumeId\":1,\"jobId\":2}");
        plan.setStatus(status);
        plan.setUpdateTime(updateTime);
        plan.setIsDeleted(0);
        return plan;
    }

    private AgentPlanStep buildStep(int stepNo, String toolName, String status) {
        AgentPlanStep step = new AgentPlanStep();
        step.setId(100L + stepNo);
        step.setPlanId(PLAN_ID);
        step.setUserId(USER_ID);
        step.setConversationId(10L);
        step.setStepNo(stepNo);
        step.setStepName("步骤" + stepNo);
        step.setToolName(toolName);
        step.setStatus(status);
        step.setIsDeleted(0);
        return step;
    }
}
