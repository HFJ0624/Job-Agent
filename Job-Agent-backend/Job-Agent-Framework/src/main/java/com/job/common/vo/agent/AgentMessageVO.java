package com.job.common.vo.agent;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.job.common.entity.agent.AiMessage;
import lombok.Data;

import java.util.Date;
/**
 * 作者:hfj
 * 功能:AI 消息展示 VO
 * 说明:
 * 1. 用于前端加载某个会话下的历史消息。
 * 2. role 用于区分 USER 和 ASSISTANT。
 * 日期: 2026/6/8 19:49
 */
@Data
public class AgentMessageVO {

    /**
     * 消息ID。
     */
    private Long id;

    /**
     * 会话ID。
     */
    private Long conversationId;

    /**
     * 消息角色：USER / ASSISTANT / SYSTEM / TOOL。
     */
    private String role;

    /**
     * 消息内容。
     */
    private String content;

    /**
     * 工具名称。
     */
    private String toolName;

    /**
     * 创建时间。
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    /**
     * Entity 转 VO。
     *
     * @param message 消息实体
     * @return 消息 VO
     */
    public static AgentMessageVO from(AiMessage message) {
        if (message == null) {
            return null;
        }

        AgentMessageVO vo = new AgentMessageVO();
        vo.setId(message.getId());
        vo.setConversationId(message.getConversationId());
        vo.setRole(message.getRole());
        vo.setContent(message.getContent());
        vo.setToolName(message.getToolName());
        vo.setCreateTime(message.getCreateTime());
        return vo;
    }
}
