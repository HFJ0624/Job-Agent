package com.job.common.entity.agent;

import com.baomidou.mybatisplus.annotation.TableName;
import com.job.common.entity.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
/**
 * 作者:hfj
 * 功能:AI 消息实体
 * 日期: 2026/6/8 15:08
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_message")
public class AiMessage extends BaseEntity {

    /**
     * 所属会话ID。
     */
    private Long conversationId;

    /**
     * 用户ID。
     */
    private Long userId;

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
     * 普通用户消息和助手消息可以为空。
     */
    private String toolName;

    /**
     * Token 数量，第一版可以不统计。
     */
    private Integer tokenCount;
}
