package com.job.bootstrap.agent.resume;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.job.bootstrap.agent.artifact.AgentArtifactService;
import com.job.bootstrap.agent.plan.AgentPlanParameterExtractor;
import com.job.bootstrap.agent.progress.TaskProgressPublisher;
import com.job.bootstrap.agent.search.ReactSearchProperties;
import com.job.bootstrap.agent.task.AgentTaskService;
import com.job.bootstrap.agent.task.TaskBudgetRegistry;
import com.job.bootstrap.service.JobResumeService;
import com.job.common.entity.agent.AgentPlan;
import com.job.enums.AgentPlanStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 独立简历入口状态机验证（P0）:
 * 1. COMPLETED 直接返回已保存答复，不重新执行节点；
 * 2. RUNNING 拒绝重复执行；
 * 3. 条件领取失败（并发）返回 RUNNING，不跑节点。
 */
class ResumeReactEntryTest {

    private static final Long USER_ID = 100L;
    private static final Long PLAN_ID = 700L;

    private final ReactSearchProperties properties = new ReactSearchProperties();
    private final AgentTaskService taskService = mock(AgentTaskService.class);
    private final ResumeAgentNode resumeAgentNode = mock(ResumeAgentNode.class);
    private final JobResumeService resumeService = mock(JobResumeService.class);
    private final AgentArtifactService artifactService = mock(AgentArtifactService.class);
    private final TaskBudgetRegistry budgetRegistry = mock(TaskBudgetRegistry.class);

    private ResumeReactEntry entry;

    @BeforeEach
    void setUp() {
        properties.setEnabled(true);
        entry = new ResumeReactEntry(properties, taskService, resumeAgentNode,
                artifactService, new AgentPlanParameterExtractor(), budgetRegistry,
                new ResumeSelectionService(resumeService), new ObjectMapper());
    }

    private AgentPlan plan(String status) {
        AgentPlan plan = new AgentPlan();
        plan.setId(PLAN_ID);
        plan.setUserId(USER_ID);
        plan.setRuntimeMode(ResumeReactEntry.RUNTIME_RESUME);
        plan.setStatus(status);
        plan.setModelCallsUsed(0);
        plan.setToolCallsUsed(0);
        plan.setExecutionMillisUsed(0);
        return plan;
    }

    @Test
    void completedTaskShouldReturnStoredAnswerWithoutRunningNode() {
        AgentPlan plan = plan(AgentPlanStatus.COMPLETED.name());
        plan.setFinalAnswer("上次的分析结果");
        when(taskService.loadOwned(USER_ID, PLAN_ID)).thenReturn(plan);

        ResumeReactEntry.Result result = entry.execute(USER_ID, 10L, PLAN_ID, "trace", "分析简历",
                TaskProgressPublisher.NOOP);

        assertThat(result.status()).isEqualTo("COMPLETED");
        assertThat(result.answer()).isEqualTo("上次的分析结果");
        verify(resumeAgentNode, never()).execute(anyLong(), anyLong(), anyLong(), anyString(),
                any(), anyString(), anyString(), any());
    }

    @Test
    void runningTaskShouldBeRejected() {
        when(taskService.loadOwned(USER_ID, PLAN_ID)).thenReturn(plan(AgentPlanStatus.RUNNING.name()));

        ResumeReactEntry.Result result = entry.execute(USER_ID, 10L, PLAN_ID, "trace", "分析简历",
                TaskProgressPublisher.NOOP);

        assertThat(result.status()).isEqualTo("RUNNING");
        verify(resumeAgentNode, never()).execute(anyLong(), anyLong(), anyLong(), anyString(),
                any(), anyString(), anyString(), any());
    }

    @Test
    void claimFailureShouldNotRunNode() {
        when(taskService.loadOwned(USER_ID, PLAN_ID))
                .thenReturn(plan(AgentPlanStatus.NEED_CLARIFICATION.name()));
        when(taskService.claimForExecution(eq(PLAN_ID), anyString())).thenReturn(false);

        ResumeReactEntry.Result result = entry.execute(USER_ID, 10L, PLAN_ID, "trace", "用第一份",
                TaskProgressPublisher.NOOP);

        assertThat(result.status()).isEqualTo("RUNNING");
        verify(resumeAgentNode, never()).execute(anyLong(), anyLong(), anyLong(), anyString(),
                any(), anyString(), anyString(), any());
    }
}
