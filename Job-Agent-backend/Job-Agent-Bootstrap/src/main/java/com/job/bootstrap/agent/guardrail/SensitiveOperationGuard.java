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

        // 空工具定义或只读工具直接放行
        // 设计决策：只读操作不会产生任何副作用，无需用户确认
        if (schema == null || AgentToolSideEffectType.READ_ONLY.equals(schema.getSideEffectType())) {
            return;
        }

        // 判断是否需要用户确认，且当前上下文中没有确认记录
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

        // 1.工具显式配置了需要确认（最高优先级）
        if (Boolean.TRUE.equals(schema.getRequiresUserConfirmation())) {
            return true;
        }

        //2.高风险副作用类型强制确认
        if (AgentToolSideEffectType.EXTERNAL_ACTION.equals(schema.getSideEffectType())
                || AgentToolSideEffectType.UPDATE_USER_STATE.equals(schema.getSideEffectType())) {
            return true;
        }

        // 3.关键词匹配检测（兜底机制）
        // 拼接工具的所有文本信息进行不区分大小写的匹配
        String text = (
                nullToEmpty(schema.getToolName())
                        + " "
                        + nullToEmpty(schema.getDisplayName())
                        + " "
                        + nullToEmpty(schema.getDescription())
        ).toLowerCase(Locale.ROOT);

        // 匹配中英文敏感操作关键词
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

    //空值处理工具方法
    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
