package com.job.common.dto.ai;

import lombok.Data;

/**
 * 作者:hfj
 * 功能:AI 模型路由分页查询参数
 * 日期:2026/6/21
 */
@Data
public class AiModelRouteQueryDTO {

    /**
     * 当前页码。
     */
    private Long pageNum = 1L;

    /**
     * 每页条数。
     */
    private Long pageSize = 10L;

    /**
     * 业务场景编码。
     */
    private String sceneCode;

    /**
     * Prompt 编码。
     */
    private String promptCode;

    /**
     * 主模型编码。
     */
    private String primaryModelCode;

    /**
     * 状态。
     */
    private String status;
}
