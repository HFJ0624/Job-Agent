package com.job.bootstrap.rag.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 作者:hfj
 * 功能:RAG 文档类型枚举
 * 日期:2026/6/14
 */
@Getter
@RequiredArgsConstructor
public enum RagDocumentType {

    /**
     * 用户简历。
     */
    RESUME("简历"),

    /**
     * 岗位 JD。
     */
    JOB("岗位JD"),

    /**
     * 公司信息。
     */
    COMPANY("公司信息"),

    /**
     * 沟通主记录。
     */
    COMMUNICATION("沟通记录"),

    /**
     * 沟通消息流水。
     */
    COMMUNICATION_MESSAGE("沟通消息");

    private final String description;
}
