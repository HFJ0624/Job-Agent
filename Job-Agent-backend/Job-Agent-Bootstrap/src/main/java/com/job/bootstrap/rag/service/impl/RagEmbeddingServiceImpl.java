package com.job.bootstrap.rag.service.impl;

import com.job.bootstrap.rag.config.RagProperties;
import com.job.bootstrap.rag.service.RagEmbeddingService;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 作者:hfj
 * 功能:RAG Embedding 服务实现
 * 日期:2026/6/14
 */
@Service
public class RagEmbeddingServiceImpl implements RagEmbeddingService {

    private final EmbeddingModel embeddingModel;
    private final RagProperties ragProperties;

    public RagEmbeddingServiceImpl(
            @Qualifier("ragEmbeddingModel") EmbeddingModel embeddingModel,
            RagProperties ragProperties
    ) {
        this.embeddingModel = embeddingModel;
        this.ragProperties = ragProperties;
    }

    /**
     * 将文本转换成 embedding 向量。
     *
     * @param text 待向量化文本
     * @return embedding 向量
     */
    @Override
    public float[] embed(String text) {
        if (!StringUtils.hasText(text)) {
            throw new IllegalArgumentException("RAG 向量化文本不能为空");
        }

        /*
         * 1. LangChain4j 负责兼容 OpenAI Embedding 协议。
         * 2. 这里不直接关心具体供应商，只关心最终返回的 float[] 向量。
         * 3. 后续如果切换模型供应商，只需要改 RagConfig 里的 EmbeddingModel Bean。
         */
        Embedding embedding = embeddingModel.embed(text).content();
        float[] vector = embedding.vector();

        validateVector(vector);
        return vector;
    }

    private void validateVector(float[] vector) {
        Integer expectedDimension = ragProperties.getPgvector().getDimension();
        if (vector == null || vector.length == 0) {
            throw new IllegalStateException("Embedding 模型返回了空向量");
        }
        if (expectedDimension != null && vector.length != expectedDimension) {
            throw new IllegalStateException(
                    "Embedding 向量维度不匹配，配置维度=" + expectedDimension + "，实际维度=" + vector.length
            );
        }

        for (float value : vector) {
            if (!Float.isFinite(value)) {
                throw new IllegalStateException("Embedding 向量中存在非法数值");
            }
        }
    }
}
