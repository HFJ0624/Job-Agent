package com.job.bootstrap.agent.compensation;

import com.job.bootstrap.agent.executor.AgentPlanExecutionResult;
import com.job.bootstrap.agent.executor.AgentPlanStepExecutionResult;
import com.job.bootstrap.agent.executor.AgentToolExecutionResult;
import com.job.bootstrap.agent.schema.AgentToolSchemaRegistry;
import com.job.bootstrap.observability.AgentObservationRecord;
import com.job.bootstrap.service.AgentObservationService;
import com.job.common.agent.tool.AgentToolSchema;
import com.job.common.entity.agent.AgentPlan;
import com.job.enums.AgentObservationEventType;
import com.job.enums.AgentObservationStatus;
import com.job.enums.AgentPlanStepStatus;
import com.job.enums.AgentToolSideEffectType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AgentPlanCompensator 单元测试（任务 G9）。
 *
 * 覆盖重点:
 * 1. 逆序补偿：stepNo 大的先撤；
 * 2. 只读工具跳过（不调动作、不记事件）；
 * 3. 未注册的写工具 → NO_ACTION 事件（不静默）；
 * 4. 不可逆工具 → IRREVERSIBLE 事件；
 * 5. 单步补偿失败 → FAILED 事件且不阻断后续补偿；
 * 6. 幂等命中（历史采用）的步骤不补偿；
 * 7. 汇总 describe 正确。
 */
class AgentPlanCompensatorTest {

    private final AgentToolSchemaRegistry schemaRegistry = mock(AgentToolSchemaRegistry.class);
    private final AgentObservationService observationService = mock(AgentObservationService.class);

    // 记录调用顺序的假动作
    private final List<String> invocations = new ArrayList<>();
    private final FakeAction greetingAction = new FakeAction("GreetingGenerateTool.generateGreeting", CompensationKind.COMPENSATABLE);
    private final FakeAction matchAction = new FakeAction("JobMatchTool.matchJob", CompensationKind.COMPENSATABLE);
    private final FakeAction emailAction = new FakeAction("EmailConnectorTool.sendEmail", CompensationKind.IRREVERSIBLE);
    private final FakeAction failingAction = new FakeAction("InterviewPrepareTool.prepareInterview", CompensationKind.COMPENSATABLE) {
        @Override
        public void compensate(Long userId, Long planId, AgentPlanStepExecutionResult stepResult) {
            invocations.add(toolName());
            throw new IllegalStateException("补偿炸了");
        }
    };

    private AgentPlanCompensator compensator;

    private static final Long USER_ID = 100L;
    private static final Long PLAN_ID = 7L;

    @BeforeEach
    void setUp() {
        AgentCompensationRegistry registry = new AgentCompensationRegistry(
                List.of(greetingAction, matchAction, emailAction, failingAction));
        compensator = new AgentPlanCompensator(registry, schemaRegistry, observationService);

        when(schemaRegistry.find("JobSearchTool.searchJobs")).thenReturn(Optional.of(readOnlySchema()));
        when(schemaRegistry.find("RagSearchTool.searchKnowledge")).thenReturn(Optional.of(readOnlySchema()));
    }

    private AgentToolSchema readOnlySchema() {
        AgentToolSchema schema = new AgentToolSchema();
        schema.setSideEffectType(AgentToolSideEffectType.READ_ONLY);
        return schema;
    }

    private AgentPlan plan() {
        AgentPlan plan = new AgentPlan();
        plan.setId(PLAN_ID);
        plan.setUserId(USER_ID);
        plan.setConversationId(10L);
        plan.setTraceId("trace-g9");
        return plan;
    }

    private AgentPlanStepExecutionResult completed(int stepNo, String toolName, boolean idempotentHit) {
        return AgentPlanStepExecutionResult.builder()
                .stepId((long) (100 + stepNo))
                .stepNo(stepNo)
                .stepName("步骤" + stepNo)
                .toolName(toolName)
                .status(AgentPlanStepStatus.COMPLETED.name())
                .resultSummary("ok")
                .idempotentHit(idempotentHit)
                .toolResult(AgentToolExecutionResult.builder()
                        .success(true).toolName(toolName).dataJson("{\"id\":1}").build())
                .build();
    }

    @Test
    void shouldCompensateInReverseStepOrder() {
        invocations.clear();
        AgentPlan plan = plan();
        List<AgentPlanStepExecutionResult> results = List.of(
                completed(1, "GreetingGenerateTool.generateGreeting", false),
                completed(2, "JobSearchTool.searchJobs", false), // 只读，跳过
                completed(3, "JobMatchTool.matchJob", false)
        );

        AgentPlanCompensator.CompensationSummary summary = compensator.compensate(plan, results);

        // stepNo 大的先撤
        assertThat(invocations).containsExactly("JobMatchTool.matchJob", "GreetingGenerateTool.generateGreeting");
        assertThat(summary.compensated()).isEqualTo(2);
        assertThat(summary.describe()).contains("已补偿 2 个副作用步骤");
    }

    @Test
    void readOnlyToolShouldBeSkippedSilently() {
        invocations.clear();
        compensator.compensate(plan(), List.of(
                completed(1, "JobSearchTool.searchJobs", false),
                completed(2, "RagSearchTool.searchKnowledge", false)));

        assertThat(invocations).isEmpty();
        // 只读工具不记事件（无观测噪音）
        verify(observationService, never()).recordEvent(any());
    }

    @Test
    void unregisteredWriteToolShouldRecordNoAction() {
        compensator.compensate(plan(), List.of(completed(1, "ResumeAnalyzeTool.analyzeResume", false)));

        ArgumentCaptor<AgentObservationRecord> captor = ArgumentCaptor.forClass(AgentObservationRecord.class);
        verify(observationService, times(1)).recordEvent(captor.capture());
        assertThat(captor.getValue().getEventType()).isEqualTo(AgentObservationEventType.COMPENSATION);
        assertThat((java.util.Map<String, Object>) captor.getValue().getRequestSnapshot()).containsEntry("kind", "NO_ACTION");
    }

    @Test
    void irreversibleToolShouldRecordIrreversibleEvent() {
        compensator.compensate(plan(), List.of(completed(1, "EmailConnectorTool.sendEmail", false)));

        ArgumentCaptor<AgentObservationRecord> captor = ArgumentCaptor.forClass(AgentObservationRecord.class);
        verify(observationService, times(1)).recordEvent(captor.capture());
        assertThat((java.util.Map<String, Object>) captor.getValue().getRequestSnapshot()).containsEntry("kind", "IRREVERSIBLE");
        assertThat(captor.getValue().getStatus()).isEqualTo(AgentObservationStatus.SUCCESS);
    }

    @Test
    void failedCompensationShouldNotBlockOthers() {
        invocations.clear();
        AgentPlanCompensator.CompensationSummary summary = compensator.compensate(plan(), List.of(
                completed(1, "GreetingGenerateTool.generateGreeting", false),
                completed(2, "InterviewPrepareTool.prepareInterview", false) // 补偿抛异常
        ));

        // 失败的补偿不影响 step1 的补偿
        assertThat(invocations).containsExactly("InterviewPrepareTool.prepareInterview", "GreetingGenerateTool.generateGreeting");
        assertThat(summary.failed()).isEqualTo(1);
        assertThat(summary.compensated()).isEqualTo(1);
        assertThat(summary.describe()).contains("1 个补偿失败");

        ArgumentCaptor<AgentObservationRecord> captor = ArgumentCaptor.forClass(AgentObservationRecord.class);
        verify(observationService, times(2)).recordEvent(captor.capture());
        assertThat(captor.getAllValues().stream()
                .anyMatch(event -> event.getStatus() == AgentObservationStatus.FAILED)).isTrue();
    }

    @Test
    void idempotentHitStepShouldNotBeCompensated() {
        invocations.clear();
        AgentPlanCompensator.CompensationSummary summary = compensator.compensate(plan(), List.of(
                completed(1, "GreetingGenerateTool.generateGreeting", true) // 历史采用，非本轮执行
        ));

        assertThat(invocations).isEmpty();
        assertThat(summary).isEqualTo(AgentPlanCompensator.CompensationSummary.EMPTY);
        verify(observationService, never()).recordEvent(any());
    }

    // 记录调用的假动作（内部类：需要把调用记到外部测试实例的 invocations）
    private class FakeAction implements AgentCompensationAction {

        private final String toolName;
        private final CompensationKind kind;

        private FakeAction(String toolName, CompensationKind kind) {
            this.toolName = toolName;
            this.kind = kind;
        }

        @Override
        public String toolName() {
            return toolName;
        }

        @Override
        public CompensationKind kind() {
            return kind;
        }

        @Override
        public void compensate(Long userId, Long planId, AgentPlanStepExecutionResult stepResult) {
            invocations.add(toolName);
        }
    }
}
