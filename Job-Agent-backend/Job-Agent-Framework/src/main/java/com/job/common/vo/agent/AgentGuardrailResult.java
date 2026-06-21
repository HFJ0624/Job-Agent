package com.job.common.vo.agent;

import com.job.enums.AgentGuardrailAction;
import com.job.enums.AgentGuardrailRiskType;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 作者:hfj
 * 功能:Agent 护栏检查结果
 * 日期:2026/6/21
 */
@Data
@Builder
public class AgentGuardrailResult {

    /**
     * 护栏最终动作。
     */
    private AgentGuardrailAction action;

    /**
     * 风险类型。
     */
    private AgentGuardrailRiskType riskType;

    /**
     * 风险等级，0 表示无风险，数字越大风险越高。
     */
    private Integer riskLevel;

    /**
     * 内部风险说明，写 Trace 用。
     */
    private String message;

    /**
     * 展示给普通用户的提示。
     */
    private String userMessage;

    /**
     * 命中的规则名称。
     */
    private List<String> matchedRules;

    /**
     * 脱敏或降级后的文本。
     */
    private String sanitizedText;

    /**
     * 附加上下文。
     */
    private Map<String, Object> metadata;

    public static AgentGuardrailResult allow() {
        return AgentGuardrailResult.builder()
                .action(AgentGuardrailAction.ALLOW)
                .riskType(AgentGuardrailRiskType.NONE)
                .riskLevel(0)
                .matchedRules(List.of())
                .metadata(Map.of())
                .build();
    }

    public boolean blocked() {
        return AgentGuardrailAction.BLOCK.equals(action);
    }

    public boolean warned() {
        return AgentGuardrailAction.WARN.equals(action);
    }
}
