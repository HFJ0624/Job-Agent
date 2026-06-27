package com.job.bootstrap.rag.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * RAG 文档类型枚举。
 */
@Getter
@RequiredArgsConstructor
public enum RagDocumentType {

    /**
     * 用户简历知识。
     */
    RESUME("简历"),

    /**
     * 岗位 JD 知识。
     */
    JOB("岗位JD"),

    /**
     * 公司资料知识。
     */
    COMPANY("公司信息"),

    /**
     * 求职沟通主记录。
     */
    COMMUNICATION("沟通记录"),

    /**
     * 求职沟通消息流水。
     */
    COMMUNICATION_MESSAGE("沟通消息"),

    /**
     * AI 模拟面试题库知识。
     */
    INTERVIEW_QUESTION("面试题库");

    private final String description;
}
