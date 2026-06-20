package com.job.common.agent.tool;

import com.job.enums.AgentToolConfirmationType;
import com.job.enums.AgentToolPermissionType;
import com.job.enums.AgentToolSideEffectType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 作者:hfj
 * 功能:Agent 工具统一 Schema
 * 日期:2026/6/20
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentToolSchema {

    /**
     * 工具唯一名称。
     * 建议格式为 ClassName.methodName，例如 JobMatchTool.matchJob。
     */
    private String toolName;

    /**
     * 后台展示名称。
     */
    private String displayName;

    /**
     * 工具分类。
     * 例如 resume、job、interview、rag。
     */
    private String category;

    /**
     * Schema 版本。
     * 第一版先用 v1；后续如果工具入参或副作用策略变更，可以按版本演进。
     */
    private String version;

    /**
     * 工具说明。
     */
    private String description;

    /**
     * Java 类名。
     */
    private String javaClassName;

    /**
     * Java 方法名。
     */
    private String javaMethodName;

    /**
     * 权限类型。
     */
    private AgentToolPermissionType permissionType;

    /**
     * 副作用类型。
     */
    private AgentToolSideEffectType sideEffectType;

    /**
     * 是否有副作用。
     * 这个字段是为了让 Planner 和后台页面不用理解 sideEffectType 的细分枚举。
     */
    private Boolean hasSideEffect;

    /**
     * 用户确认策略。
     */
    private AgentToolConfirmationType confirmationType;

    /**
     * 是否需要用户确认。
     */
    private Boolean requiresUserConfirmation;

    /**
     * 需要确认时展示给用户的风险说明。
     */
    private String confirmationMessage;

    /**
     * 入参 Schema 列表。
     */
    private List<AgentToolParamSchema> inputParams;

    /**
     * 出参 Schema 列表。
     */
    private List<AgentToolOutputSchema> outputFields;

    /**
     * 错误 Schema 列表。
     */
    private List<AgentToolErrorSchema> errorCodes;
}
