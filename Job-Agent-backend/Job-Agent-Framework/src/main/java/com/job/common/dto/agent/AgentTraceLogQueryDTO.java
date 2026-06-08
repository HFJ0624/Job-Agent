package com.job.common.dto.agent;

import lombok.Data;

/**
 * 作者:hfj
 * 功能:Agent Trace 日志分页查询参数
 * 说明:
 * 1. 后台页面通过这些字段筛选 Agent 调用日志。
 * 2. pageNum 和 pageSize 用于分页。
 * 3. 其他字段都是可选筛选条件。
 * 日期: 2026/6/8 20:03
 */
@Data
public class AgentTraceLogQueryDTO {

    /**
     * 当前页码。
     */
    private Long pageNum = 1L;

    /**
     * 每页条数。
     */
    private Long pageSize = 10L;

    /**
     * 链路ID。
     * 可以用于定位一次完整 Agent 调用。
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
     * 例如：AGENT_CHAT、RESUME_SCORE、JOB_MATCH、GREETING_GENERATE。
     */
    private String intentCode;

    /**
     * 工具名称。
     * 例如：ResumeAnalyzeTool、JobMatchTool、GreetingGenerateTool。
     */
    private String toolName;

    /**
     * 状态。
     * SUCCESS / FAILED。
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
