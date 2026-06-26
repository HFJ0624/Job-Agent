package com.job.common.entity.agent;

import com.baomidou.mybatisplus.annotation.TableName;
import com.job.common.entity.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 作者: hfj
 * 功能: Agent 长期记忆版本历史实体
 * 日期: 2026/6/25
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("agent_memory_history")
public class AgentMemoryHistory extends BaseEntity {

    /**
     * 对应 agent_long_term_memory.id。
     */
    private Long memoryId;

    /**
     * 记忆所属用户。
     */
    private Long userId;

    /**
     * 记忆类型。
     */
    private String memoryType;

    /**
     * 记忆 key。
     */
    private String memoryKey;

    /**
     * 变更类型: CREATE、UPDATE、STATUS_CHANGE。
     */
    private String changeType;

    /**
     * 旧记忆正文。
     */
    private String oldMemoryValue;

    /**
     * 新记忆正文。
     */
    private String newMemoryValue;

    /**
     * 旧摘要。
     */
    private String oldSummary;

    /**
     * 新摘要。
     */
    private String newSummary;

    /**
     * 旧状态。
     */
    private String oldStatus;

    /**
     * 新状态。
     */
    private String newStatus;

    /**
     * 是否检测到同 key 新旧内容冲突。
     */
    private Integer conflictDetected;

    /**
     * 冲突原因说明。
     */
    private String conflictReason;

    /**
     * 来源类型。
     */
    private String sourceType;

    /**
     * 来源业务 ID。
     */
    private Long sourceId;

    /**
     * 操作者类型: SYSTEM、ADMIN。
     */
    private String operatorType;
}
