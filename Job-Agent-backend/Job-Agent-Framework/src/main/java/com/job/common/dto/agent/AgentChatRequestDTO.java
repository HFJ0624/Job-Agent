package com.job.common.dto.agent;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;
/**
 * 作者:hfj
 * 功能:AI 助手对话请求
 * 日期: 2026/6/8 15:09
 */
@Data
public class AgentChatRequestDTO {

    /**
     * 会话ID。
     * 如果为空，后端自动创建新会话。
     */
    private Long conversationId;

    /**
     * 已存在的 Agent 计划ID。
     *
     * 说明:
     * 1. 第一次对话一般不传，后端会生成新计划。
     * 2. 如果上一次返回 requiresUserConfirmation=true，前端确认后应把 planId 原样传回。
     * 3. 这样 Executor 会继续执行同一份计划，而不是重新生成一份计划。
     */
    private Long planId;

    /**
     * 用户输入内容。
     */
    @NotBlank(message = "消息内容不能为空")
    @Size(max = 2000, message = "消息内容不能超过2000字")
    private String message;

    /**
     * 本轮用户已确认允许执行的工具名。
     *
     * 说明:
     * 1. 对有副作用且需要确认的工具，前端必须先让用户确认。
     * 2. 确认后把工具名放到这里，例如 GreetingGenerateTool.generateGreeting。
     * 3. 后端 Tool Guard 会再次校验，避免模型绕过确认直接调用工具。
     */
    private List<String> confirmedToolNames;

    /**
     * 是否恢复上次失败的计划。
     *
     * 说明:
     * 1. 默认 false，保持原有语义：已失败的计划不会自动重跑。
     * 2. 前端重试时传 true，并原样传回 planId 和 confirmedToolNames，
     *    Executor 会从第一个未完成步骤继续，已完成的步骤不会重复执行。
     */
    private Boolean retry;
}
