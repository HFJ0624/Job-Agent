package com.job.enums;

/**
 * 作者:hfj
 * 功能:RAG 索引状态
 * 日期:2026/6/20
 */
public enum RagIndexStatus {

    /**
     * 等待写入向量索引。
     */
    PENDING,

    /**
     * 已成功写入向量索引。
     */
    INDEXED,

    /**
     * 写入向量索引失败。
     */
    FAILED
}
