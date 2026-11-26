package com.job.ai.agent.port;

/**
 * 功能: 文本向量化端口（Infra-Ai 定义，Bootstrap 实现）。
 *
 * <p>语义缓存/记忆召回只需要"文本 → 向量"，具体模型与批处理由宿主 RAG 能力负责。</p>
 */
public interface EmbeddingPort {

    float[] embed(String text);
}
