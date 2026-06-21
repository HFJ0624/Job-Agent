package com.job.common.dto.ai;

import lombok.Data;

/**
 * 作者:hfj
 * 功能:AI 模型配置分页查询参数
 * 日期:2026/6/21
 */
@Data
public class AiModelConfigQueryDTO {

    /**
     * 当前页码。
     */
    private Long pageNum = 1L;

    /**
     * 每页条数。
     */
    private Long pageSize = 10L;

    /**
     * 模型编码，支持模糊查询。
     */
    private String modelCode;

    /**
     * 模型名称，支持模糊查询。
     */
    private String modelName;

    /**
     * 供应商，例如 OPENAI、DEEPSEEK、QWEN、OLLAMA。
     */
    private String provider;

    /**
     * 配置状态，ACTIVE 表示启用，DISABLED 表示停用。
     */
    private String status;
}
