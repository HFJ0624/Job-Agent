package com.job.common.dto.agent;

import lombok.Data;

/**
 * 作者:hfj
 * 功能:Agent 计划分页查询参数
 * 日期:2026/6/19
 */
@Data
public class AgentPlanQueryDTO {

    /**
     * 当前页码。
     */
    private Long pageNum = 1L;

    /**
     * 每页条数。
     */
    private Long pageSize = 10L;

    /**
     * TraceId。
     */
    private String traceId;

    /**
     * 用户ID。
     */
    private Long userId;

    /**
     * 会话ID。
     */
    private Long conversationId;

    /**
     * 意图编码。
     */
    private String intentCode;

    /**
     * 计划状态。
     */
    private String status;

    /**
     * 开始时间，格式：yyyy-MM-dd HH:mm:ss。
     */
    private String startTime;

    /**
     * 结束时间，格式：yyyy-MM-dd HH:mm:ss。
     */
    private String endTime;
}
