package com.job.common.dto.rag;

import lombok.Data;

/**
 * 作者:hfj
 * 功能:RAG 文档分页查询参数
 * 日期:2026/6/20
 */
@Data
public class RagDocumentQueryDTO {

    /**
     * 当前页码。
     */
    private Long pageNum = 1L;

    /**
     * 每页条数。
     */
    private Long pageSize = 10L;

    private Long userId;

    private String documentType;

    private Long businessId;

    private String title;

    private String permissionScope;

    private String status;

    private String indexStatus;

    /**
     * 同时匹配标题、来源和元数据。
     */
    private String keyword;

    private String startTime;

    private String endTime;
}
