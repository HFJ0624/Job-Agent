package com.job.common.entity.agent;

import com.baomidou.mybatisplus.annotation.TableName;
import com.job.common.entity.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
/**
 * 作者:hfj
 * 功能:AI 会话实体
 * 日期: 2026/6/8 15:07
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_conversation")
public class AiConversation extends BaseEntity {

    /**
     * 会话所属用户。
     */
    private Long userId;

    /**
     * 会话标题。
     */
    private String title;

    /**
     * 会话类型，例如 JOB_AGENT。
     */
    private String conversationType;
}
