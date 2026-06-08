package com.job.common.vo.agent;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.job.common.entity.agent.AiConversation;
import lombok.Data;

import java.util.Date;
/**
 * 作者:hfj
 * 功能:AI 会话列表展示 VO
 * 说明:
 * 1. 这个 VO 返回给前端左侧会话列表使用。
 * 2. 不直接返回 Entity，是为了避免暴露无关字段。
 * 日期: 2026/6/8 19:48
 */
@Data
public class AgentConversationVO {

    /**
     * 会话ID。
     */
    private Long id;

    /**
     * 会话标题。
     */
    private String title;

    /**
     * 会话类型，例如 JOB_AGENT。
     */
    private String conversationType;

    /**
     * 创建时间。
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    /**
     * 更新时间。
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updateTime;

    /**
     * Entity 转 VO。
     *
     * @param conversation 会话实体
     * @return 会话 VO
     */
    public static AgentConversationVO from(AiConversation conversation) {
        if (conversation == null) {
            return null;
        }

        AgentConversationVO vo = new AgentConversationVO();
        vo.setId(conversation.getId());
        vo.setTitle(conversation.getTitle());
        vo.setConversationType(conversation.getConversationType());
        vo.setCreateTime(conversation.getCreateTime());
        vo.setUpdateTime(conversation.getUpdateTime());
        return vo;
    }
}
