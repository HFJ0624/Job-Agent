package com.job.common.entity.rag;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.job.common.entity.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

/**
 * 作者:hfj
 * 功能:RAG 业务文档实体
 * 日期:2026/6/20
 *
 * 说明:
 * 1. 这张表保存 admin 可见的“知识文档”维度。
 * 2. 一个文档会被切成多个 rag_chunk，再写入 pgvector。
 * 3. pgvector 只负责向量检索，本表负责权限、来源、状态和后台可视化。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("rag_document")
public class RagDocument extends BaseEntity {

    /**
     * 知识所属用户，0 表示公共知识。
     */
    private Long userId;

    /**
     * 文档类型，例如 RESUME、JOB、COMPANY。
     */
    private String documentType;

    /**
     * 来源业务表主键 ID。
     */
    private Long businessId;

    /**
     * 文档标题。
     */
    private String title;

    /**
     * 来源标识，例如 resume、job_position。
     */
    private String source;

    /**
     * 权限范围，PUBLIC 或 PRIVATE。
     */
    private String permissionScope;

    /**
     * 完整文档内容 hash，用于判断来源内容是否变化。
     */
    private String contentHash;

    /**
     * 当前文档切块数量。
     */
    private Integer chunkCount;

    /**
     * 文档状态，ACTIVE 或 DELETED。
     */
    private String status;

    /**
     * 索引状态，PENDING、INDEXED 或 FAILED。
     */
    private String indexStatus;

    /**
     * 索引失败原因。
     */
    private String errorMsg;

    /**
     * 最近一次索引时间。
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date lastIndexTime;

    /**
     * 结构化元数据 JSON。
     */
    private String metadataJson;
}
