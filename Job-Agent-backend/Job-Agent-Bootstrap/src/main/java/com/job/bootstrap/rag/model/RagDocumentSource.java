package com.job.bootstrap.rag.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 作者:hfj
 * 功能:RAG 待索引业务文档
 * 日期:2026/6/14
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RagDocumentSource {

    /**
     * 知识归属用户 ID。
     * 0 表示公共知识，例如岗位和公司。
     */
    private Long userId;

    /**
     * 文档类型。
     */
    private RagDocumentType documentType;

    /**
     * 原业务表主键 ID。
     */
    private Long businessId;

    /**
     * 文档标题。
     */
    private String title;

    /**
     * 待切片和向量化的完整文本。
     */
    private String content;

    /**
     * 来源描述。
     */
    private String source;

    /**
     * 结构化元数据。
     */
    @Builder.Default
    private Map<String, Object> metadata = new LinkedHashMap<>();
}
