package com.job.common.vo.rag;

import lombok.Data;

/**
 * 作者:hfj
 * 功能:RAG 文档类型统计返回对象
 * 日期:2026/6/14
 */
@Data
public class RagDocumentTypeStatsVO {

    /**
     * 知识归属用户 ID。
     * 0 表示公共知识；大于 0 表示某个普通用户的私有知识。
     */
    private Long userId;

    /**
     * 文档类型。
     * 例如 RESUME/JOB/COMPANY/COMMUNICATION/COMMUNICATION_MESSAGE。
     */
    private String documentType;

    /**
     * 业务文档数量。
     * 一个业务文档可能会切成多个 chunk。
     */
    private Integer documentCount;

    /**
     * 文本分片数量。
     */
    private Integer chunkCount;
}
