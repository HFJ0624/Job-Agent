package com.job.common.dto.ai;

import lombok.Data;

/**
 * 作者:hfj
 * 功能:AI 模型调用日志分页查询参数
 * 日期:2026/6/21
 */
@Data
public class AiModelCallLogQueryDTO {

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
     * 用户 ID。
     */
    private Long userId;

    /**
     * 业务场景编码。
     */
    private String sceneCode;

    /**
     * 模型编码。
     */
    private String modelCode;

    /**
     * 调用状态。
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
