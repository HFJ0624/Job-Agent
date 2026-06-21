package com.job.enums;

/**
 * 作者:hfj
 * 功能:RAG 知识状态
 * 日期:2026/6/20
 */
public enum RagKnowledgeStatus {

    /**
     * 可被检索和展示。
     */
    ACTIVE,

    /**
     * 来源业务数据已删除或下线，不再参与召回。
     */
    DELETED
}
