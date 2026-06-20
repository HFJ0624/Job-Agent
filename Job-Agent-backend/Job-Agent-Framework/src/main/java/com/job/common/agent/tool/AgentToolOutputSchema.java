package com.job.common.agent.tool;

import com.job.enums.AgentToolValueType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 作者:hfj
 * 功能:Agent 工具出参 Schema
 * 日期:2026/6/20
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentToolOutputSchema {

    /**
     * 返回字段名。
     */
    private String name;

    /**
     * 返回字段类型。
     */
    private AgentToolValueType type;

    /**
     * 字段说明。
     */
    private String description;

    /**
     * 是否可能为空。
     */
    private Boolean nullable;

    /**
     * 示例值。
     */
    private String example;
}
