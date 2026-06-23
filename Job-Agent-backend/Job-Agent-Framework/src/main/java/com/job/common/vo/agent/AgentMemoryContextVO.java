package com.job.common.vo.agent;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 作者: hfj
 * 功能: 单轮 Agent 对话使用的长期记忆上下文
 * 日期: 2026/6/23
 *
 * 说明:
 * 1. 该对象不是数据库表，它是“本轮允许进入 Prompt 的记忆包”。
 * 2. 里面只放画像摘要和少量相关记忆，避免长期记忆越多，Prompt 越来越大。
 */
@Data
public class AgentMemoryContextVO {

    /**
     * 压缩后的用户画像摘要，来自 agent_user_memory_profile。
     */
    private String profileSummary;

    /**
     * 本轮问题相关的少量长期记忆。
     */
    private List<AgentMemoryVO> memories = new ArrayList<>();

    /**
     * 最终可注入 Prompt 的文本。
     */
    private String promptContext;

    /**
     * 粗略 token 估算。
     *
     * 第一版用字符数 / 2 做中文场景下的保守估算，不追求绝对精确，只用于预算裁剪。
     */
    private Integer estimatedTokens;

    /**
     * 是否因为 token 预算被裁剪。
     */
    private Boolean truncated;
}
