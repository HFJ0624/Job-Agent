package com.job.common.dto.rag;

import lombok.Data;

/**
 * 作者:hfj
 * 功能:RAG 召回质量评测参数
 * 日期:2026/6/20
 */
@Data
public class RagRetrievalEvalDTO {

    /**
     * 当前评测用户 ID。
     * 0 表示只评测公共知识；大于 0 时会同时召回公共知识和该用户私有知识。
     */
    private Long userId = 0L;

    /**
     * 评测问题。
     */
    private String query;

    /**
     * 召回条数。
     */
    private Integer limit;

    /**
     * 期望命中的 RAG 切块 ID。
     * 如果后台已经知道具体切块，优先传这个字段，命中判断最准确。
     */
    private Long expectedChunkId;

    /**
     * 期望命中的 RAG 文档 ID。
     * 当不关心具体 chunkIndex，只关心是否命中文档时使用。
     */
    private Long expectedDocumentId;

    /**
     * 期望命中的业务文档类型。
     * 可和 expectedBusinessId 一起使用，例如 JOB + 岗位 ID。
     */
    private String expectedDocumentType;

    /**
     * 期望命中的业务 ID。
     */
    private Long expectedBusinessId;

    /**
     * 期望结果中应该包含的关键词。
     * 用于没有明确文档 ID 时做轻量评测。
     */
    private String expectedKeyword;
}
