package com.job.common.dto.agent;

import lombok.Data;

/**
 * 作者: hfj
 * 功能: Agent 统一观测事件分页查询参数
 * 日期: 2026/6/22
 */
@Data
public class AgentObservationEventQueryDTO {

    /**
     * 当前页码。
     */
    private Long pageNum = 1L;

    /**
     * 每页条数。
     */
    private Long pageSize = 10L;

    /**
     * TraceId，用于串起一次完整 Agent 请求。
     */
    private String traceId;

    /**
     * 用户 ID。
     */
    private Long userId;

    /**
     * 会话 ID。
     */
    private Long conversationId;

    /**
     * Agent 计划 ID。
     */
    private Long planId;

    /**
     * 计划步骤 ID。
     */
    private Long stepId;

    /**
     * 模型业务场景编码。
     */
    private String sceneCode;

    /**
     * Agent 意图编码。
     */
    private String intentCode;

    /**
     * 事件类型，MODEL/TOOL/EXECUTOR/GUARDRAIL/RAG/TRACE。
     */
    private String eventType;

    /**
     * 事件名称，例如 AGENT_SUMMARY 或 JobMatchTool.matchJob。
     */
    private String eventName;

    /**
     * 事件状态，SUCCESS/FAILED/BLOCKED/SKIPPED。
     */
    private String status;

    /**
     * 失败分类。
     */
    private String errorCategory;

    /**
     * 模型编码。
     */
    private String modelCode;

    /**
     * 工具名称。
     */
    private String toolName;

    /**
     * 开始时间，格式 yyyy-MM-dd HH:mm:ss。
     */
    private String startTime;

    /**
     * 结束时间，格式 yyyy-MM-dd HH:mm:ss。
     */
    private String endTime;
}
