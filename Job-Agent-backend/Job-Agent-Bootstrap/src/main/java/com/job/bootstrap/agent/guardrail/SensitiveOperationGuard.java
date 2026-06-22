package com.job.bootstrap.agent.guardrail;

import com.job.bootstrap.agent.context.AgentRuntimeContext;
import com.job.common.agent.tool.AgentToolSchema;
import com.job.enums.AgentToolErrorCode;
import com.job.enums.AgentToolSideEffectType;
import com.job.exception.AgentToolException;
import org.springframework.stereotype.Component;

/**
 * 作者:hfj
 * 功能:Agent敏感操作拦截守护组件
 * 在Agent执行任何工具调用前进行安全检查，强制要求用户确认高风险操作
 * 属于Agent系统的"操作安全防线"，防止大模型自主执行可能造成不可逆影响的敏感操作
 * 日期:2026/6/21
 */
@Component
public class SensitiveOperationGuard {

    /***
     * 校验敏感操作是否允许执行
     *
     * @param schema 待执行工具的元数据定义
     */
    public void assertSensitiveOperationAllowed(AgentToolSchema schema) {

        // 1. 空工具定义或只读工具直接放行。
        // 设计决策：只读操作不会产生任何副作用，无需用户确认。
        if (schema == null || AgentToolSideEffectType.READ_ONLY.equals(schema.getSideEffectType())) {
            return;
        }

        // 2. 只有明确需要确认的工具才检查 confirmedToolNames。
        // 关键原因：
        // - 是否需要确认应由 Tool Schema 和副作用类型决定，不能靠描述里的关键词猜测。
        // - 岗位匹配工具的描述里有“投递建议”，旧关键词规则会把它误判成真实投递操作。
        // - 真实高风险工具仍然会通过 REQUIRED_BEFORE_EXECUTION、EXTERNAL_ACTION 或 UPDATE_USER_STATE 强制确认。
        if (mustConfirm(schema) && !AgentRuntimeContext.isToolConfirmed(schema.getToolName())) {
            throw new AgentToolException(
                    AgentToolErrorCode.TOOL_CONFIRMATION_REQUIRED,
                    schema.getToolName(),
                    // 优先使用工具自定义的确认消息，没有则使用默认消息
                    schema.getConfirmationMessage() == null
                            ? "该敏感操作需要用户确认后才能执行"
                            : schema.getConfirmationMessage()
            );
        }
    }

    /***
     * 判断工具是否需要用户确认才能执行
     *
     * @param schema 待执行工具的元数据定义
     * @return true表示需要用户确认，false表示不需要
     */
    private boolean mustConfirm(AgentToolSchema schema) {

        // 1. 工具显式配置了需要确认，最高优先级。
        if (Boolean.TRUE.equals(schema.getRequiresUserConfirmation())) {
            return true;
        }

        // 2. 高风险副作用类型强制确认。
        // WRITE_BUSINESS_RECORD 不在这里强制确认，因为简历评分、岗位匹配、面试准备都只是生成站内分析记录。
        return AgentToolSideEffectType.EXTERNAL_ACTION.equals(schema.getSideEffectType())
                || AgentToolSideEffectType.UPDATE_USER_STATE.equals(schema.getSideEffectType());
    }
}
