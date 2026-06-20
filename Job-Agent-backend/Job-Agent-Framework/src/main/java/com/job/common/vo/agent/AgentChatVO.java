package com.job.common.vo.agent;

import lombok.Data;

import java.util.List;

/**
 * 作者:hfj
 * 功能:AI 助手对话返回结果
 * 日期: 2026/6/8 15:10
 */
@Data
public class AgentChatVO {

    /**
     * 会话ID。
     */
    private Long conversationId;

    /**
     * 本轮对话关联的 Agent 计划ID。
     */
    private Long planId;

    /**
     * 助手回复内容。
     */
    private String answer;

    /**
     * 本轮是否需要用户确认后才能继续执行工具。
     */
    private Boolean requiresUserConfirmation;

    /**
     * 需要用户确认的工具名。
     */
    private List<String> requiredConfirmationToolNames;

    /**
     * 给前端展示的确认提示。
     */
    private String confirmationMessage;
}
