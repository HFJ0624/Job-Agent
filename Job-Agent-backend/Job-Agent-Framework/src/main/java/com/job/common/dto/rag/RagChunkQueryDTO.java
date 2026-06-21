package com.job.common.dto.rag;

import lombok.Data;

/**
 * 作者:hfj
 * 功能:RAG 切块分页查询参数
 * 日期:2026/6/20
 */
@Data
public class RagChunkQueryDTO {

    private Long pageNum = 1L;

    private Long pageSize = 10L;

    private Long documentId;

    private Long userId;

    private String documentType;

    private Long businessId;

    private String title;

    private String status;

    private String vectorStatus;

    /**
     * 匹配切块标题、正文和元数据。
     */
    private String keyword;

    private String startTime;

    private String endTime;
}
