package com.job.common.vo.agent;

import lombok.Data;
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
     * 助手回复内容。
     */
    private String answer;
}
