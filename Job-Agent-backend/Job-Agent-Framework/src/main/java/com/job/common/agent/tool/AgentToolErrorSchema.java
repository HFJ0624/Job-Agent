package com.job.common.agent.tool;

import com.job.enums.AgentToolErrorCode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 作者:hfj
 * 功能:Agent 工具错误 Schema
 * 日期:2026/6/20
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentToolErrorSchema {

    /**
     * 统一错误码。
     */
    private AgentToolErrorCode code;

    /**
     * 面向后台排查的错误说明。
     */
    private String message;

    /**
     * 面向普通用户的提示。
     */
    private String userMessage;

    /**
     * 是否允许用户补充信息后重试。
     */
    private Boolean retryable;
}
