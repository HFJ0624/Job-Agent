package com.job.common.dto.agent;

import lombok.Data;

/**
 * 作者:hfj
 * 功能:Agent 长期记忆分页查询参数
 * 日期:2026/6/20
 */
@Data
public class AgentMemoryQueryDTO {

    /**
     * 当前页码。
     */
    private Long pageNum = 1L;

    /**
     * 每页条数。
     */
    private Long pageSize = 10L;

    /**
     * 用户 ID。
     */
    private Long userId;

    /**
     * 记忆类型。
     */
    private String memoryType;

    /**
     * 记忆键。
     */
    private String memoryKey;

    /**
     * 来源类型。
     */
    private String sourceType;

    /**
     * 记忆状态。
     */
    private String status;

    /**
     * 关键词，会匹配 memoryKey、summary 和 memoryValue。
     */
    private String keyword;

    /**
     * 开始时间，格式: yyyy-MM-dd HH:mm:ss。
     */
    private String startTime;

    /**
     * 结束时间，格式: yyyy-MM-dd HH:mm:ss。
     */
    private String endTime;
}
