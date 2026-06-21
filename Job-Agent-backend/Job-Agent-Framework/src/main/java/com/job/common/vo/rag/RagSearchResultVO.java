package com.job.common.vo.rag;

import lombok.Data;

import java.util.Map;

/**
 * 作者:hfj
 * 功能:RAG 相似度检索结果返回对象
 * 日期:2026/6/14
 */
@Data
public class RagSearchResultVO {

    /**
     * 向量库分片主键 ID。
     */
    private Long id;

    /**
     * RAG 文档 ID，对应 MySQL rag_document.id。
     */
    private Long documentId;

    /**
     * RAG 切块 ID，对应 MySQL rag_chunk.id。
     */
    private Long chunkId;

    /**
     * 本轮引用序号，从 1 开始。
     */
    private Integer referenceNo;

    /**
     * 知识归属用户 ID。
     * 0 表示公共知识，例如岗位 JD 和公司信息。
     */
    private Long userId;

    /**
     * 文档类型。
     * 例如 RESUME/JOB/COMPANY/COMMUNICATION/COMMUNICATION_MESSAGE。
     */
    private String documentType;

    /**
     * 原业务表主键 ID。
     */
    private Long businessId;

    /**
     * 当前文本分片序号。
     */
    private Integer chunkIndex;

    /**
     * 检索结果标题，方便前端和 Agent 快速识别来源。
     */
    private String title;

    /**
     * 被召回的文本分片内容。
     */
    private String content;

    /**
     * 来源描述。
     */
    private String source;

    /**
     * 权限范围，PUBLIC 或 PRIVATE。
     */
    private String permissionScope;

    /**
     * 引用标题，用于前端和 Agent 展示来源。
     */
    private String referenceTitle;

    /**
     * 最终排序得分。
     * 混合检索开启后，这里保存重排序后的得分，越接近 1 表示越应该排在前面。
     */
    private Double score;

    /**
     * 向量相似度得分。
     */
    private Double vectorScore;

    /**
     * 关键词匹配得分。
     */
    private Double keywordScore;

    /**
     * 重排序得分。
     */
    private Double rerankScore;

    /**
     * 召回来源: VECTOR、KEYWORD 或 HYBRID。
     */
    private String retrievalSource;

    /**
     * 结构化元数据。
     * 例如岗位城市、公司行业、沟通状态、简历文件名等。
     */
    private Map<String, Object> metadata;
}
