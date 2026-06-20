package com.job.common.entity.agent;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.job.common.entity.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 作者:hfj
 * 功能:Agent 长期记忆实体
 * 日期:2026/6/20
 *
 * 说明:
 * 1. 这张表保存“跨对话仍然有价值”的用户事实。
 * 2. memoryKey 用于稳定更新同一类事实，例如 preferred_city、target_role。
 * 3. memoryValue 保存完整记忆内容，summary 保存后台和提示词里更容易阅读的短摘要。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("agent_long_term_memory")
public class AgentLongTermMemory extends BaseEntity {

    /**
     * 记忆所属用户 ID。
     */
    private Long userId;

    /**
     * 记忆类型，对应 AgentMemoryType。
     */
    private String memoryType;

    /**
     * 记忆键，同一用户、同一类型下可用它覆盖更新稳定事实。
     */
    private String memoryKey;

    /**
     * 记忆正文，保存 Agent 后续可使用的事实内容。
     */
    private String memoryValue;

    /**
     * 记忆短摘要，便于后台列表和模型提示词快速阅读。
     */
    private String summary;

    /**
     * 来源类型，对应 AgentMemorySourceType。
     */
    private String sourceType;

    /**
     * 来源业务 ID，例如 planId、stepId 或后台录入记录 ID。
     */
    private Long sourceId;

    /**
     * 置信度，范围建议 0-1。
     */
    private BigDecimal confidence;

    /**
     * 重要性，范围建议 0-1；检索时会优先召回高重要性的记忆。
     */
    private BigDecimal importance;

    /**
     * 记忆状态，对应 AgentMemoryStatus。
     */
    private String status;

    /**
     * 最近一次被召回使用的时间。
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date lastUsedTime;
}
