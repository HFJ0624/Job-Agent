package com.job.bootstrap.agent.memory;

import com.job.enums.AgentMemoryType;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 作者: hfj
 * 功能: 单条候选长期记忆
 * 日期: 2026/6/23
 *
 * 说明:
 * 1. 候选记忆还不是最终入库记录，必须经过 MemoryWritePolicy 校验后才能保存。
 * 2. 这样把“抽取”和“是否值得记”分开，后续增加 LLM 抽取、人工审核、敏感信息过滤时不会互相污染。
 */
@Data
@Builder
public class AgentMemoryCandidate {

    /**
     * 记忆类型，例如 USER_PREFERENCE、COMMUNICATION_STYLE。
     */
    private AgentMemoryType memoryType;

    /**
     * 稳定记忆键。
     *
     * 例如 assistant_nickname、preferred_city、target_role。
     * 有稳定 key 的记忆会覆盖更新，避免同一事实反复产生多条记录。
     */
    private String memoryKey;

    /**
     * 记忆正文。
     */
    private String memoryValue;

    /**
     * 给后台和 Prompt 阅读的短摘要。
     */
    private String summary;

    /**
     * 抽取置信度，范围 0-1。
     */
    private BigDecimal confidence;

    /**
     * 记忆重要性，范围 0-1。
     */
    private BigDecimal importance;
}
