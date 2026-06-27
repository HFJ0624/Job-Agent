package com.job.common.dto.interview;

import lombok.Data;

/**
 * 后台本地题库导入参数。
 */
@Data
public class InterviewQuestionImportDTO {

    /**
     * 本地 markdown 目录。
     * 为空时使用服务端默认目录 D:\workspace\job-mcp-docs。
     */
    private String directoryPath;

    /**
     * 是否在导入后立即写入 RAG 和向量库。
     * 第一版默认开启，保证 admin 的 RAG 文档和 chunk 页面立刻可见。
     */
    private Boolean indexAfterImport = true;
}
