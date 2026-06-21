package com.job.common.entity.rag;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.job.common.entity.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

/**
 * 作者:hfj
 * 功能:RAG 文本切块实体
 * 日期:2026/6/20
 *
 * 说明:
 * 1. 这张表保存 admin 可见的“切块内容”。
 * 2. 每条切块都会对应 pgvector 中的一条向量记录。
 * 3. 检索命中后通过 chunkId 回到本表，展示引用来源和内容片段。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("rag_chunk")
public class RagChunk extends BaseEntity {

    /**
     * 所属 RAG 文档 ID。
     */
    private Long documentId;

    /**
     * 知识所属用户，0 表示公共知识。
     */
    private Long userId;

    /**
     * 文档类型。
     */
    private String documentType;

    /**
     * 来源业务表主键 ID。
     */
    private Long businessId;

    /**
     * 当前文档内的切块序号，从 0 开始。
     */
    private Integer chunkIndex;

    /**
     * 切块标题，通常继承文档标题。
     */
    private String title;

    /**
     * 切块正文。
     */
    private String content;

    /**
     * 切块内容 hash。
     */
    private String contentHash;

    /**
     * 来源标识。
     */
    private String source;

    /**
     * 元数据 JSON。
     */
    private String metadataJson;

    /**
     * 切块状态，ACTIVE 或 DELETED。
     */
    private String status;

    /**
     * 向量写入状态，PENDING、INDEXED 或 FAILED。
     */
    private String vectorStatus;

    /**
     * 最近一次向量索引时间。
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date lastIndexTime;
}
