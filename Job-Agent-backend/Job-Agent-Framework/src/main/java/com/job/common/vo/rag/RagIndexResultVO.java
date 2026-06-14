package com.job.common.vo.rag;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 作者:hfj
 * 功能:RAG 知识库索引结果返回对象
 * 日期:2026/6/14
 */
@Data
public class RagIndexResultVO {

    /**
     * 本次真正写入向量库的业务文档数量。
     */
    private Integer indexedDocumentCount = 0;

    /**
     * 本次真正写入向量库的文本分片数量。
     */
    private Integer indexedChunkCount = 0;

    /**
     * 已索引的简历数量。
     */
    private Integer resumeCount = 0;

    /**
     * 已索引的岗位 JD 数量。
     */
    private Integer jobCount = 0;

    /**
     * 已索引的公司数量。
     */
    private Integer companyCount = 0;

    /**
     * 已索引的沟通主记录数量。
     */
    private Integer communicationCount = 0;

    /**
     * 已索引的沟通消息流水数量。
     */
    private Integer messageCount = 0;

    /**
     * 因内容为空而跳过的文档数量。
     */
    private Integer skippedDocumentCount = 0;

    /**
     * 索引过程中的提示信息。
     */
    private List<String> warnings = new ArrayList<>();

    /**
     * 记录一次文档索引成功。
     *
     * @param chunkCount 当前文档切出来的分片数量
     */
    public void addIndexedDocument(int chunkCount) {
        this.indexedDocumentCount++;
        this.indexedChunkCount += chunkCount;
    }

    /**
     * 记录一次空内容跳过。
     */
    public void addSkippedDocument() {
        this.skippedDocumentCount++;
    }
}
