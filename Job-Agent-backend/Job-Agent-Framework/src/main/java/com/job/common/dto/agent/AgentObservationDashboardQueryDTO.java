package com.job.common.dto.agent;

import lombok.Data;

/**
 * 作者: hfj
 * 功能: Agent 观测看板查询参数
 * 日期: 2026/6/22
 */
@Data
public class AgentObservationDashboardQueryDTO {

    /**
     * 开始时间，格式 yyyy-MM-dd HH:mm:ss。
     */
    private String startTime;

    /**
     * 结束时间，格式 yyyy-MM-dd HH:mm:ss。
     */
    private String endTime;

    /**
     * 事件类型，可为空。
     */
    private String eventType;

    /**
     * 模型编码，可为空。
     */
    private String modelCode;

    /**
     * 工具名称，可为空。
     */
    private String toolName;
}
