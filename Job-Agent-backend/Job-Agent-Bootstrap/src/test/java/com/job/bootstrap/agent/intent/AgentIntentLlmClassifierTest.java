package com.job.bootstrap.agent.intent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.job.bootstrap.service.AiModelGatewayService;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * AgentIntentLlmClassifier 单元测试（任务5 阶段一）。
 *
 * 覆盖重点:
 * 1. 合法 intentCode + 高置信度 → 返回意图。
 * 2. 低置信度 / 非法 intentCode → 空（保持规则结果）。
 * 3. ```json 围栏输出能正确解析。
 * 4. 网关调用失败 → 降级为空，不抛异常。
 * 5. 空消息不调网关。
 */
class AgentIntentLlmClassifierTest {

    private final AiModelGatewayService gateway = mock(AiModelGatewayService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AgentIntentLlmClassifier classifier = new AgentIntentLlmClassifier(gateway, objectMapper);

    @Test
    void validIntentWithHighConfidenceShouldReturn() {
        when(gateway.chat(eq("INTENT_CLASSIFY"), any(), anyString(), anyLong(), any()))
                .thenReturn("{\"intentCode\":\"RESUME_ANALYZE\",\"confidence\":0.9}");
        Optional<AgentIntentCode> result = classifier.classify(1L, "trace", "帮我分析一下简历");
        assertThat(result).contains(AgentIntentCode.RESUME_ANALYZE);
    }

    @Test
    void lowConfidenceShouldReturnEmpty() {
        when(gateway.chat(eq("INTENT_CLASSIFY"), any(), anyString(), anyLong(), any()))
                .thenReturn("{\"intentCode\":\"RESUME_ANALYZE\",\"confidence\":0.3}");
        assertThat(classifier.classify(1L, "trace", "随便说点什么")).isEmpty();
    }

    @Test
    void illegalIntentCodeShouldReturnEmpty() {
        when(gateway.chat(eq("INTENT_CLASSIFY"), any(), anyString(), anyLong(), any()))
                .thenReturn("{\"intentCode\":\"DROP_TABLE\",\"confidence\":0.99}");
        assertThat(classifier.classify(1L, "trace", "x")).isEmpty();
    }

    @Test
    void fencedJsonShouldBeParsed() {
        when(gateway.chat(eq("INTENT_CLASSIFY"), any(), anyString(), anyLong(), any()))
                .thenReturn("```json\n{\"intentCode\":\"JOB_MATCH\",\"confidence\":0.8}\n```");
        assertThat(classifier.classify(1L, "trace", "匹配吗")).contains(AgentIntentCode.JOB_MATCH);
    }

    @Test
    void gatewayFailureShouldDegradeToEmpty() {
        when(gateway.chat(eq("INTENT_CLASSIFY"), any(), anyString(), anyLong(), any()))
                .thenThrow(new RuntimeException("场景未配置"));
        assertThat(classifier.classify(1L, "trace", "x")).isEmpty();
    }

    @Test
    void blankMessageShouldNotCallGateway() {
        assertThat(classifier.classify(1L, "trace", "   ")).isEmpty();
        verifyNoInteractions(gateway);
    }
}
