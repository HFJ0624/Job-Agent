package com.job.common.dto.agent;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
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
}
