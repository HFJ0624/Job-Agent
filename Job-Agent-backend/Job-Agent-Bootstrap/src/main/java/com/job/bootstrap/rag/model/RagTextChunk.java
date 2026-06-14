package com.job.bootstrap.rag.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 作者:hfj
 * 功能:RAG 文本分片模型
 * 日期:2026/6/14
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RagTextChunk {

    private Long userId;

    private RagDocumentType documentType;

    private Long businessId;

    private Integer chunkIndex;

    private String title;

    private String content;

    private String source;

    @Builder.Default
    private Map<String, Object> metadata = new LinkedHashMap<>();

    /**
     * Embedding 模型返回的向量。
     */
    private float[] embedding;

    /**
     * 文本内容哈希。
     * 后续可以用于增量索引时判断内容是否变化。
     */
    private String contentHash;
}
