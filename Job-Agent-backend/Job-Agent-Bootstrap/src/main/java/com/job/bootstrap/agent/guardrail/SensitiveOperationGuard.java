package com.job.bootstrap.agent.guardrail;

import com.job.bootstrap.agent.context.AgentRuntimeContext;
import com.job.common.agent.tool.AgentToolSchema;
import com.job.enums.AgentToolErrorCode;
import com.job.enums.AgentToolSideEffectType;
import com.job.exception.AgentToolException;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * 作者:hfj
 * 功能:敏感操作拦截
 * 日期:2026/6/21
 */
@Component
public class SensitiveOperationGuard {

    /**
     * 校验敏感操作是否允许执行。
     *
     * 方法步骤:
     * 1. READ_ONLY 工具没有业务写入，直接放行。
     * 2. EXTERNAL_ACTION 和 UPDATE_USER_STATE 风险最高，第一版必须要求用户确认。
     * 3. WRITE_BUSINESS_RECORD 中只有发送、删除、投递、沟通创建等敏感动作必须确认。
     * 4. 简历分析、岗位匹配、面试准备这类报告类写入暂时不强制确认，符合本轮确认方案。
     */
    public void assertSensitiveOperationAllowed(AgentToolSchema schema) {
        if (schema == null || AgentToolSideEffectType.READ_ONLY.equals(schema.getSideEffectType())) {
            return;
        }

        if (mustConfirm(schema) && !AgentRuntimeContext.isToolConfirmed(schema.getToolName())) {
            throw new AgentToolException(
                    AgentToolErrorCode.TOOL_CONFIRMATION_REQUIRED,
                    schema.getToolName(),
                    schema.getConfirmationMessage() == null
                            ? "该敏感操作需要用户确认后才能执行"
                            : schema.getConfirmationMessage()
            );
        }
    }

    private boolean mustConfirm(AgentToolSchema schema) {
        if (Boolean.TRUE.equals(schema.getRequiresUserConfirmation())) {
            return true;
        }
        if (AgentToolSideEffectType.EXTERNAL_ACTION.equals(schema.getSideEffectType())
                || AgentToolSideEffectType.UPDATE_USER_STATE.equals(schema.getSideEffectType())) {
            return true;
        }

        String text = (
                nullToEmpty(schema.getToolName())
                        + " "
                        + nullToEmpty(schema.getDisplayName())
                        + " "
                        + nullToEmpty(schema.getDescription())
        ).toLowerCase(Locale.ROOT);

        return text.contains("delete")
                || text.contains("remove")
                || text.contains("send")
                || text.contains("submit")
                || text.contains("apply")
                || text.contains("删除")
                || text.contains("发送")
                || text.contains("投递")
                || text.contains("沟通记录");
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
