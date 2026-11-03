package com.job.bootstrap.agent.search;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.job.bootstrap.agent.plan.AgentPlanParameterExtractor;
import com.job.bootstrap.agent.react.AgentResult;
import com.job.bootstrap.agent.run.AgentRunService;
import com.job.bootstrap.ai.turn.AiChatMessage;
import com.job.bootstrap.service.AgentTraceService;
import com.job.bootstrap.service.UserJobPreferenceService;
import com.job.common.entity.agent.AgentRun;
import com.job.enums.AgentRunStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * JobSearchReactEntry 单元测试（第一阶段）。
 *
 * 覆盖重点（文档测试清单 6/9）:
 * 1. 开关默认关闭 → 不接管（旧链路零影响）；
 * 2. 开启后岗位搜索意图接管；携带已有计划的 run 按所属任务路由；
 * 3. 重复请求（claim 失败）→ 拒绝并提示执行中；
 * 4. 补参续跑：WAITING_INPUT 的 run 复用完整消息序列；
 * 5. 状态映射：FOUND/EMPTY → SUCCEEDED，NEED_INPUT → WAITING_INPUT，FAILED → FAILED。
 */
class JobSearchReactEntryTest {

    private static final Long USER_ID = 100L;
    private static final Long CONVERSATION_ID = 10L;

    private final ReactSearchProperties properties = new ReactSearchProperties();
    private final AgentRunService runService = mock(AgentRunService.class);
    private final JobSearchWorkflow workflow = mock(JobSearchWorkflow.class);
    private final AgentPlanParameterExtractor extractor = mock(AgentPlanParameterExtractor.class);
    private final UserJobPreferenceService preferenceService = mock(UserJobPreferenceService.class);
    private final AgentTraceService traceService = mock(AgentTraceService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    private JobSearchReactEntry entry;

    @BeforeEach
    void setUp() {
        entry = new JobSearchReactEntry(properties, runService, workflow, extractor,
                preferenceService, traceService, objectMapper);
        when(extractor.extract(anyString())).thenReturn(Map.of("keyword", "Java", "city", "上海"));
        when(preferenceService.getPreference(anyLong())).thenReturn(null);
    }

    private AgentRun run(Long id, String status, int attempt) {
        AgentRun run = new AgentRun();
        run.setId(id);
        run.setAttempt(attempt);
        run.setStatus(status);
        run.setAgentName(JobSearchWorkflow.AGENT_NAME);
        run.setCreateTime(new Date());
        return run;
    }

    @Test
    void disabledSwitchShouldNotHandle() {
        properties.setEnabled(false);

        assertThat(entry.shouldHandle("JOB_SEARCH", null)).isFalse();
    }

    @Test
    void enabledShouldHandleJobSearchIntent() {
        properties.setEnabled(true);

        assertThat(entry.shouldHandle("JOB_SEARCH", null)).isTrue();
        // 非岗位搜索且无已有关联 run → 不接管
        assertThat(entry.shouldHandle("RESUME_ANALYZE", null)).isFalse();
    }

    @Test
    void planWithExistingRunShouldBeRoutedByTask() {
        properties.setEnabled(true);
        when(runService.findLatestByPlan(88L))
                .thenReturn(run(5L, AgentRunStatus.SUCCEEDED.name(), 1));

        // 当前意图不相关，但计划下有本 Agent 的 run → 按任务路由接管
        assertThat(entry.shouldHandle("GENERAL_CHAT", 88L)).isTrue();
    }

    @Test
    void duplicateRequestShouldBeRejectedWhenClaimFails() {
        properties.setEnabled(true);
        when(runService.create(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(run(5L, AgentRunStatus.PENDING.name(), 0));
        when(runService.claim(5L, 0)).thenReturn(false);

        JobSearchReactEntry.Result result = entry.execute(USER_ID, CONVERSATION_ID, null, "trace", "JOB_SEARCH", "找岗位");

        assertThat(result.handled()).isTrue();
        assertThat(result.status()).isEqualTo("REJECTED");
        assertThat(result.answer()).contains("正在执行");
        verify(workflow, never()).execute(any());
    }

    @Test
    void needInputShouldPersistWaitingInputStatus() {
        properties.setEnabled(true);
        when(runService.create(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(run(5L, AgentRunStatus.PENDING.name(), 0));
        when(runService.claim(5L, 0)).thenReturn(true);
        when(runService.finish(any(), anyInt(), any(), any(), any(), any())).thenReturn(true);
        when(workflow.execute(any())).thenReturn(new JobSearchWorkflow.WorkflowOutcome(
                AgentResult.Outcome.NEED_INPUT, null, "你希望在哪个城市？", List.of(),
                List.of("city"), "你希望在哪个城市？", List.of(), 0, 1));

        JobSearchReactEntry.Result result = entry.execute(USER_ID, CONVERSATION_ID, null, "trace", "JOB_SEARCH", "找岗位");

        assertThat(result.status()).isEqualTo(AgentRunStatus.WAITING_INPUT.name());
        verify(runService).finish(eq(5L), eq(1), eq(AgentRunStatus.WAITING_INPUT), any(), any(), any());
    }

    @Test
    void resumeShouldReusePersistedMessages() throws Exception {
        properties.setEnabled(true);
        AgentRun waiting = run(5L, AgentRunStatus.WAITING_INPUT.name(), 1);
        waiting.setMessagesJson(objectMapper.writeValueAsString(List.of(
                AiChatMessage.system("系统提示"),
                AiChatMessage.user("找上海 Java"),
                AiChatMessage.tool("c1", "{\"jobs\":[]}")
        )));
        when(runService.findLatestByPlan(88L)).thenReturn(waiting);
        when(runService.claim(5L, 1)).thenReturn(true);
        when(runService.finish(any(), anyInt(), any(), any(), any(), any())).thenReturn(true);
        when(workflow.execute(any())).thenReturn(new JobSearchWorkflow.WorkflowOutcome(
                AgentResult.Outcome.EMPTY, null, "没有找到符合条件的岗位。", List.of(),
                List.of(), null, List.of(), 0, 1));

        entry.execute(USER_ID, CONVERSATION_ID, 88L, "trace", "JOB_SEARCH", "那杭州呢");

        ArgumentCaptor<JobSearchWorkflow.WorkflowInput> captor =
                ArgumentCaptor.forClass(JobSearchWorkflow.WorkflowInput.class);
        verify(workflow).execute(captor.capture());
        // 续跑复用完整历史消息（含 tool 结果），而不是从头开始
        assertThat(captor.getValue().resumeMessages()).hasSize(3);
        assertThat(captor.getValue().resumeMessages().get(2).toolCallId()).isEqualTo("c1");
        // 不新建 run
        verify(runService, never()).create(any(), any(), any(), any(), any(), any(), any());
    }
}
