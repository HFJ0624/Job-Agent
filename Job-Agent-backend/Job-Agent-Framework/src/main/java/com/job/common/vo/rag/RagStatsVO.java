package com.job.common.vo.rag;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 作者:hfj
 * 功能:RAG 知识库统计返回对象
 * 日期:2026/6/14
 */
@Data
public class RagStatsVO {

    /**
     * pgvector 表名。
     */
    private String tableName;

    /**
     * embedding 向量维度。
     */
    private Integer dimension;

    /**
     * 默认召回数量。
     */
    private Integer maxResults;

    /**
     * 最低相似度阈值。
     */
    private Double minScore;

    /**
     * 文本切片大小。
     */
    private Integer chunkSize;

    /**
     * 文本切片重叠长度。
     */
    private Integer chunkOverlap;

    /**
     * pgvector 扩展是否已经在当前 RAG 数据库启用。
     */
    private Boolean extensionReady = false;

    /**
     * RAG 向量表是否已经存在。
     */
    private Boolean tableReady = false;

    /**
     * RAG Schema 是否已经可以正常使用。
     */
    private Boolean schemaReady = false;

    /**
     * 初始化提示信息。
     * 当扩展、表或权限不满足时，后台页面直接展示这个字段。
     */
    private String setupMessage;

    /**
     * 总分片数量。
     */
    private Integer totalChunks = 0;

    /**
     * 公共知识分片数量。
     */
    private Integer publicChunks = 0;

    /**
     * 用户私有知识分片数量。
     */
    private Integer privateChunks = 0;

    /**
     * 按用户和文档类型分组的统计。
     */
    private List<RagDocumentTypeStatsVO> typeStats = new ArrayList<>();
}
