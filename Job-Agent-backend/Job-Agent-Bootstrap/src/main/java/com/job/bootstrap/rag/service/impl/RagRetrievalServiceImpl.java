package com.job.bootstrap.rag.service.impl;

import com.job.bootstrap.rag.config.RagProperties;
import com.job.bootstrap.rag.service.RagEmbeddingService;
import com.job.bootstrap.rag.service.RagRetrievalService;
import com.job.bootstrap.rag.service.RagVectorStoreService;
import com.job.common.vo.rag.RagSearchResultVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 作者:hfj
 * 功能:RAG 检索服务实现
 * 日期:2026/6/14
 */
@Service
@RequiredArgsConstructor
public class RagRetrievalServiceImpl implements RagRetrievalService {

    private final RagEmbeddingService ragEmbeddingService;
    private final RagVectorStoreService ragVectorStoreService;
    private final RagProperties ragProperties;

    /**
     * 根据用户问题召回相关知识。
     *
     * @param userId 当前登录用户 ID
     * @param query 用户问题或检索词
     * @param limit 召回数量，为空时使用默认配置
     * @return 相似度最高的知识分片
     */
    @Override
    public List<RagSearchResultVO> search(Long userId, String query, Integer limit) {
        if (userId == null) {
            throw new IllegalArgumentException("userId 不能为空");
        }
        if (!StringUtils.hasText(query)) {
            throw new IllegalArgumentException("RAG 检索问题不能为空");
        }

        int actualLimit = resolveLimit(limit);

        /*
         * 1. 先把用户问题转成向量。
         * 2. 再用同一个向量空间去 pgvector 中做 cosine 相似度检索。
         * 3. 检索范围包含 user_id=0 的公共知识和当前 userId 的私有知识。
         */
        float[] queryVector = ragEmbeddingService.embed(query.trim());
        return ragVectorStoreService.search(
                userId,
                queryVector,
                actualLimit,
                ragProperties.getRetrieval().getMinScore()
        );
    }

    private int resolveLimit(Integer limit) {
        int defaultLimit = ragProperties.getRetrieval().getMaxResults();
        if (limit == null || limit <= 0) {
            return defaultLimit;
        }

        /*
         * 限制最大召回数量，避免一次工具调用把太多分片塞进大模型上下文。
         */
        return Math.min(limit, 20);
    }
}
