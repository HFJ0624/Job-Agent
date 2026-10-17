package com.job.bootstrap.agent.plan;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.job.bootstrap.service.AiModelGatewayService;
import com.job.enums.AiModelTier;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * AgentPlanParamLlmExtractor 单元测试（任务5 阶段二）。
 *
 * 覆盖重点:
 * 1. 白名单 key + 类型正确 → 接收（ID→Long、minSalary→Integer、名称→String）。
 * 2. 白名单外的 key → 丢弃。
 * 3. 类型不符 → 丢弃。
 * 4. ```json 围栏能解析。
 * 5. 网关失败 → 降级为空 Map；allowedKeys 为空不调网关。
 */
class AgentPlanParamLlmExtractorTest {

    private final AiModelGatewayService gateway = mock(AiModelGatewayService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AgentPlanParamLlmExtractor extractor = new AgentPlanParamLlmExtractor(gateway, objectMapper);

    @Test
    void shouldReturnWhitelistedAndTypedParams() {
        when(gateway.chat(eq("PARAM_EXTRACT"), eq(AiModelTier.LIGHT), any(), anyString(), anyLong(), any()))
                .thenReturn("{\"resumeId\":\"12\",\"jobTitle\":\"Java 后端\",\"minSalary\":\"15000\"}");

        Map<String, Object> result = extractor.extract(1L, "trace", "分析简历12应聘Java后端",
                Set.of("resumeId", "jobTitle", "minSalary"));

        assertThat(result.get("resumeId")).isEqualTo(12L);
        assertThat(result.get("jobTitle")).isEqualTo("Java 后端");
        assertThat(result.get("minSalary")).isEqualTo(15000);
    }

    @Test
    void shouldDropNonWhitelistedKey() {
        when(gateway.chat(eq("PARAM_EXTRACT"), eq(AiModelTier.LIGHT), any(), anyString(), anyLong(), any()))
                .thenReturn("{\"resumeId\":1,\"evilKey\":\"x\"}");

        Map<String, Object> result = extractor.extract(1L, "trace", "m", Set.of("resumeId"));

        assertThat(result).containsOnlyKeys("resumeId");
    }

    @Test
    void shouldDropWrongType() {
        when(gateway.chat(eq("PARAM_EXTRACT"), eq(AiModelTier.LIGHT), any(), anyString(), anyLong(), any()))
                .thenReturn("{\"resumeId\":\"abc\"}");

        Map<String, Object> result = extractor.extract(1L, "trace", "m", Set.of("resumeId"));

        assertThat(result).isEmpty();
    }

    @Test
    void fencedJsonShouldBeParsed() {
        when(gateway.chat(eq("PARAM_EXTRACT"), eq(AiModelTier.LIGHT), any(), anyString(), anyLong(), any()))
                .thenReturn("```json\n{\"city\":\"杭州\"}\n```");

        Map<String, Object> result = extractor.extract(1L, "trace", "m", Set.of("city"));

        assertThat(result.get("city")).isEqualTo("杭州");
    }

    @Test
    void gatewayFailureShouldDegradeToEmpty() {
        when(gateway.chat(eq("PARAM_EXTRACT"), eq(AiModelTier.LIGHT), any(), anyString(), anyLong(), any()))
                .thenThrow(new RuntimeException("boom"));
        assertThat(extractor.extract(1L, "trace", "m", Set.of("resumeId"))).isEmpty();
    }

    @Test
    void emptyAllowedKeysShouldNotCallGateway() {
        assertThat(extractor.extract(1L, "trace", "m", Set.of())).isEmpty();
        verifyNoInteractions(gateway);
    }
}
