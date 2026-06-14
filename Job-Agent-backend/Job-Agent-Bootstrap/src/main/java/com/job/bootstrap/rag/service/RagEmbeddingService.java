package com.job.bootstrap.rag.service;

/**
 * 作者:hfj
 * 功能:RAG Embedding 服务接口
 * 日期:2026/6/14
 */
public interface RagEmbeddingService {

    /**
     * 将文本转换为向量。
     *
     * @param text 待向量化文本
     * @return embedding 向量
     */
    float[] embed(String text);
}
