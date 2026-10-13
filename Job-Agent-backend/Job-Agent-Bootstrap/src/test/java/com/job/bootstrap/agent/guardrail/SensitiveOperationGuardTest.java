package com.job.bootstrap.agent.guardrail;

import com.job.bootstrap.agent.context.AgentRuntimeContext;
import com.job.common.agent.tool.AgentToolSchema;
import com.job.enums.AgentToolErrorCode;
import com.job.enums.AgentToolSideEffectType;
import com.job.exception.AgentToolException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * SensitiveOperationGuard 单元测试。
 *
 * 覆盖重点:
 * 1. 空 Schema / 只读工具直接放行，无需确认。
 * 2. 高风险副作用（EXTERNAL_ACTION / UPDATE_USER_STATE）且用户未确认 -> 抛 TOOL_CONFIRMATION_REQUIRED。
 * 3. 高风险副作用但用户已确认本轮工具 -> 放行。
 * 4. WRITE_BUSINESS_RECORD 不强制确认（简历评分/岗位匹配只生成站内分析记录）。
 * 5. 工具显式 requiresUserConfirmation=true 且未确认 -> 抛确认异常。
 *
 * 注意：确认态来自 AgentRuntimeContext（ThreadLocal），每个用例自行设置并在结束后清理。
 */
class SensitiveOperationGuardTest {

    private final SensitiveOperationGuard guard = new SensitiveOperationGuard();

    @AfterEach
    void tearDown() {
        AgentRuntimeContext.clear();
    }

    @Test
    void nullSchemaShouldPass() {
        assertThatCode(() -> guard.assertSensitiveOperationAllowed(null)).doesNotThrowAnyException();
    }

    @Test
    void readOnlyToolShouldPassWithoutConfirmation() {
        AgentToolSchema schema = AgentToolSchema.builder()
                .toolName("JobMatchTool.matchJob")
                .sideEffectType(AgentToolSideEffectType.READ_ONLY)
                .build();
        assertThatCode(() -> guard.assertSensitiveOperationAllowed(schema)).doesNotThrowAnyException();
    }

    @Test
    void externalActionWithoutConfirmationShouldBeBlocked() {
        AgentRuntimeContext.set(1L, 10L, "trace", "INTENT", List.of());
        AgentToolSchema schema = AgentToolSchema.builder()
                .toolName("EmailConnectorTool.sendEmail")
                .sideEffectType(AgentToolSideEffectType.EXTERNAL_ACTION)
                .build();

        assertThatThrownBy(() -> guard.assertSensitiveOperationAllowed(schema))
                .isInstanceOf(AgentToolException.class)
                .extracting(error -> ((AgentToolException) error).getToolErrorCode())
                .isEqualTo(AgentToolErrorCode.TOOL_CONFIRMATION_REQUIRED);
    }

    @Test
    void externalActionWithConfirmationShouldPass() {
        AgentRuntimeContext.set(1L, 10L, "trace", "INTENT", List.of("EmailConnectorTool.sendEmail"));
        AgentToolSchema schema = AgentToolSchema.builder()
                .toolName("EmailConnectorTool.sendEmail")
                .sideEffectType(AgentToolSideEffectType.EXTERNAL_ACTION)
                .build();
        assertThatCode(() -> guard.assertSensitiveOperationAllowed(schema)).doesNotThrowAnyException();
    }

    @Test
    void writeBusinessRecordShouldNotForceConfirmation() {
        AgentRuntimeContext.set(1L, 10L, "trace", "INTENT", List.of());
        AgentToolSchema schema = AgentToolSchema.builder()
                .toolName("ResumeAnalyzeTool.analyzeResume")
                .sideEffectType(AgentToolSideEffectType.WRITE_BUSINESS_RECORD)
                .requiresUserConfirmation(false)
                .build();
        assertThatCode(() -> guard.assertSensitiveOperationAllowed(schema)).doesNotThrowAnyException();
    }

    @Test
    void explicitRequiresConfirmationShouldBeBlockedWhenNotConfirmed() {
        AgentRuntimeContext.set(1L, 10L, "trace", "INTENT", List.of());
        AgentToolSchema schema = AgentToolSchema.builder()
                .toolName("ResumeAnalyzeTool.analyzeResume")
                .sideEffectType(AgentToolSideEffectType.WRITE_BUSINESS_RECORD)
                .requiresUserConfirmation(true)
                .build();

        assertThatThrownBy(() -> guard.assertSensitiveOperationAllowed(schema))
                .isInstanceOf(AgentToolException.class)
                .extracting(error -> ((AgentToolException) error).getToolErrorCode())
                .isEqualTo(AgentToolErrorCode.TOOL_CONFIRMATION_REQUIRED);
    }
}
