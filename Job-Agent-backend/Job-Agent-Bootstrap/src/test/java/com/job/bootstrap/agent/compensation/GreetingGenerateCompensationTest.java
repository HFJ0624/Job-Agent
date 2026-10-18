package com.job.bootstrap.agent.compensation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.job.bootstrap.agent.executor.AgentPlanStepExecutionResult;
import com.job.bootstrap.agent.executor.AgentToolExecutionResult;
import com.job.bootstrap.mapper.JobCommunicationRecordMapper;
import com.job.bootstrap.service.JobCommunicationRecordService;
import com.job.common.entity.communication.JobCommunicationRecord;
import com.job.enums.CommunicationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * GreetingGenerateCompensation 单元测试（任务 G9）。
 *
 * 覆盖重点:
 * 1. 正常路径：按 greetingId 反查沟通记录，状态可补偿 → closeCommunication；
 * 2. 状态守卫：已进入真实沟通（COMMUNICATED）→ 不关闭；
 * 3. 记录不存在 → 静默跳过（无异常）；
 * 4. 输出缺少 ID / 非 JSON → 抛异常（由编排器记 FAILED 事件）。
 */
class GreetingGenerateCompensationTest {

    private static final Long USER_ID = 100L;
    private static final Long PLAN_ID = 7L;

    private final JobCommunicationRecordMapper communicationMapper = mock(JobCommunicationRecordMapper.class);
    private final JobCommunicationRecordService communicationService = mock(JobCommunicationRecordService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final GreetingGenerateCompensation action =
            new GreetingGenerateCompensation(objectMapper, communicationMapper, communicationService);

    @BeforeEach
    void setUp() {
        when(communicationMapper.selectOne(any())).thenReturn(null);
    }

    private AgentPlanStepExecutionResult stepResult(String dataJson) {
        return AgentPlanStepExecutionResult.builder()
                .stepId(101L)
                .stepNo(1)
                .toolName("GreetingGenerateTool.generateGreeting")
                .status("COMPLETED")
                .toolResult(AgentToolExecutionResult.builder()
                        .success(true)
                        .toolName("GreetingGenerateTool.generateGreeting")
                        .dataJson(dataJson)
                        .build())
                .build();
    }

    private JobCommunicationRecord record(String status) {
        JobCommunicationRecord record = new JobCommunicationRecord();
        record.setId(55L);
        record.setUserId(USER_ID);
        record.setGreetingRecordId(33L);
        record.setCommunicationStatus(status);
        record.setIsDeleted(0);
        return record;
    }

    @Test
    void shouldCloseCommunicationWhenStatusIsCompensable() {
        when(communicationMapper.selectOne(any())).thenReturn(record(CommunicationStatus.GREETING_GENERATED.name()));

        action.compensate(USER_ID, PLAN_ID, stepResult("{\"id\":33,\"content\":\"您好\"}"));

        verify(communicationService).closeCommunication(USER_ID, 55L);
    }

    @Test
    void copiedStatusShouldAlsoBeCompensable() {
        when(communicationMapper.selectOne(any())).thenReturn(record(CommunicationStatus.COPIED.name()));

        action.compensate(USER_ID, PLAN_ID, stepResult("{\"id\":33}"));

        verify(communicationService).closeCommunication(USER_ID, 55L);
    }

    @Test
    void communicatedRecordShouldNotBeClosed() {
        when(communicationMapper.selectOne(any())).thenReturn(record(CommunicationStatus.COMMUNICATED.name()));

        action.compensate(USER_ID, PLAN_ID, stepResult("{\"id\":33}"));

        // 用户已真实沟通：系统不替用户关闭
        verify(communicationService, never()).closeCommunication(anyLong(), anyLong());
    }

    @Test
    void missingRecordShouldSkipSilently() {
        when(communicationMapper.selectOne(any())).thenReturn(null);

        action.compensate(USER_ID, PLAN_ID, stepResult("{\"id\":33}"));

        verify(communicationService, never()).closeCommunication(anyLong(), anyLong());
    }

    @Test
    void missingGreetingIdShouldThrow() {
        assertThatThrownBy(() -> action.compensate(USER_ID, PLAN_ID, stepResult("{\"content\":\"您好\"}")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("记录ID");
    }

    @Test
    void invalidJsonShouldThrow() {
        assertThatThrownBy(() -> action.compensate(USER_ID, PLAN_ID, stepResult("not-json")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void metadataShouldBeCorrect() {
        assertThat(action.toolName()).isEqualTo("GreetingGenerateTool.generateGreeting");
        assertThat(action.kind()).isEqualTo(CompensationKind.COMPENSATABLE);
    }
}
