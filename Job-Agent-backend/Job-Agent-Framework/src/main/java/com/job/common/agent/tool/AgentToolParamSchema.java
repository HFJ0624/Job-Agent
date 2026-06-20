package com.job.common.agent.tool;

import com.job.enums.AgentToolValueType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 作者:hfj
 * 功能:Agent 工具入参 Schema
 * 日期:2026/6/20
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentToolParamSchema {

    /**
     * 参数名。
     * 必须和 Java Tool 方法入参语义一致，便于 Planner、Guard、Trace 使用同一套字段。
     */
    private String name;

    /**
     * 参数类型。
     */
    private AgentToolValueType type;

    /**
     * 是否必填。
     * Guard 会根据这个字段做执行前校验，防止模型漏传关键参数。
     */
    private Boolean required;

    /**
     * 参数说明。
     */
    private String description;

    /**
     * 参数来源。
     * 例如 USER_INPUT、FRONTEND_CONTEXT、SYSTEM_CONTEXT。
     */
    private String source;

    /**
     * 示例值。
     */
    private String example;

    /**
     * 默认值说明。
     * 注意这里只做说明，真正默认值仍由工具或业务 Service 决定。
     */
    private String defaultValue;

    /**
     * 是否敏感。
     * 第一版用于后台展示和后续脱敏扩展。
     */
    private Boolean sensitive;
}
