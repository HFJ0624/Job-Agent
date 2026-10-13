package com.job.bootstrap.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.job.bootstrap.agent.guardrail.AgentGuardrailService;
import com.job.bootstrap.agent.guardrail.PiiMasker;
import com.job.bootstrap.mapper.AgentTraceLogMapper;
import com.job.bootstrap.service.AgentObservationService;
import com.job.common.entity.agent.AgentTraceLog;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AgentTraceServiceImpl 单元测试（聚焦 errorMsg 脱敏，任务12）。
 *
 * 覆盖重点:
 * 1. errorMsg 里的手机号等敏感信息在落库前被脱敏（复用 maskSensitiveData）。
 * 2. errorMsg 为 null 时保持 null，不触发脱敏、不 NPE。
 */
class AgentTraceServiceImplTest {

    private final AgentTraceLogMapper agentTraceLogMapper = mock(AgentTraceLogMapper.class);
    private final AgentGuardrailService agentGuardrailService = mock(AgentGuardrailService.class);
    private final AgentObservationService agentObservationService = mock(AgentObservationService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final AgentTraceServiceImpl service = new AgentTraceServiceImpl(
            agentTraceLogMapper, objectMapper, agentGuardrailService, agentObservationService);

    @Test
    void errorMsgShouldBeMaskedBeforePersist() {
        // 脱敏委托给真实 PiiMasker，验证 errorMsg 里的手机号被掩掉
        PiiMasker piiMasker = new PiiMasker();
        when(agentGuardrailService.maskSensitiveData(any()))
                .thenAnswer(invocation -> piiMasker.maskObject(invocation.getArgument(0)));

        service.saveTrace("trace-1", 100L, 10L, "JOB_MATCH", "SomeTool.foo",
                "input", "output", "FAILED", "调用失败，联系 13800138000", 5L);

        ArgumentCaptor<AgentTraceLog> captor = ArgumentCaptor.forClass(AgentTraceLog.class);
        verify(agentTraceLogMapper).insert(captor.capture());
        AgentTraceLog saved = captor.getValue();
        assertThat(saved.getErrorMsg()).contains("138****8000").doesNotContain("13800138000");
    }

    @Test
    void nullErrorMsgShouldStayNull() {
        when(agentGuardrailService.maskSensitiveData(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.saveTrace("trace-2", 100L, 10L, "JOB_MATCH", null, null, null, "SUCCESS", null, 1L);

        ArgumentCaptor<AgentTraceLog> captor = ArgumentCaptor.forClass(AgentTraceLog.class);
        verify(agentTraceLogMapper).insert(captor.capture());
        assertThat(captor.getValue().getErrorMsg()).isNull();
    }
}
