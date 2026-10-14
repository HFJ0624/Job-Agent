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

    /**
     * 更早历史的滚动摘要。
     * 多轮上下文超出预算时，由 CONTEXT_SUMMARY 场景把窗口之外的旧历史压缩成一段摘要，
     * 总结时用「滚动摘要 + 最近 N 轮原文」控制 Prompt 大小。
     */
    private String summary;

    /**
     * 摘要已覆盖到的最大消息 ID（增量滚动）。
     * 只有 ID 大于它的旧消息才是“新增待压缩”的，避免每轮重复压缩同一段历史。
     */
    private Long summaryUptoMessageId;
}
