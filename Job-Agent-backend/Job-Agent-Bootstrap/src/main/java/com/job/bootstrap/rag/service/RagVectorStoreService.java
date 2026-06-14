package com.job.bootstrap.rag.service;

import com.job.bootstrap.rag.model.RagTextChunk;
import com.job.common.vo.rag.RagSearchResultVO;
import com.job.common.vo.rag.RagStatsVO;

import java.util.Collection;
import java.util.List;

/**
 * 作者:hfj
 * 功能:pgvector 向量库访问服务接口
 * 日期:2026/6/14
 */
public interface RagVectorStoreService {

    /**
     * 确保 pgvector 扩展、向量表和索引已经存在。
     */
    void ensureSchema();

    /**
     * 删除某个用户指定类型的全部向量文档。
     *
     * @param userId 用户 ID，0 表示公共知识
     * @param documentTypes 文档类型集合
     */
    void deleteDocuments(Long userId, Collection<String> documentTypes);

    /**
     * 删除某个业务文档的所有分片。
     *
     * @param userId 用户 ID
     * @param documentType 文档类型
     * @param businessId 业务主键 ID
     */
    void deleteDocument(Long userId, String documentType, Long businessId);

    /**
     * 写入文本分片。
     *
     * @param chunks 已经带有 embedding 的文本分片
     */
    void saveChunks(List<RagTextChunk> chunks);

    /**
     * 相似度检索。
     *
     * @param userId 当前用户 ID
     * @param queryVector 问题向量
     * @param limit 召回条数
     * @param minScore 最低相似度
     * @return 召回结果
     */
    List<RagSearchResultVO> search(Long userId, float[] queryVector, int limit, double minScore);

    /**
     * 查询向量库统计信息。
     *
     * @return RAG 知识库统计
     */
    RagStatsVO getStats();
}
