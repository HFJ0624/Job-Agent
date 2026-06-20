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
}
