package com.job.common.dto.ai;

import lombok.Data;

/**
 * 作者:hfj
 * 功能:AI Prompt 模板分页查询参数
 * 日期:2026/6/21
 */
@Data
public class AiPromptTemplateQueryDTO {

    /**
     * 当前页码。
     */
    private Long pageNum = 1L;

    /**
     * 每页条数。
     */
    private Long pageSize = 10L;

    /**
     * Prompt 编码，支持模糊查询。
     */
    private String promptCode;

    /**
     * 业务场景编码，例如 AGENT_SUMMARY。
     */
    private String sceneCode;

    /**
     * 状态，ACTIVE 表示启用，DISABLED 表示停用。
     */
    private String status;
}
