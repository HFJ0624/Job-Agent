package com.job.bootstrap.rag.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.job.bootstrap.rag.model.RagDocumentSource;
import com.job.common.dto.rag.RagChunkQueryDTO;
import com.job.common.dto.rag.RagDocumentQueryDTO;
import com.job.common.entity.rag.RagChunk;
import com.job.common.vo.rag.RagChunkVO;
import com.job.common.vo.rag.RagDocumentVO;
import com.job.common.vo.rag.RagSearchResultVO;

import java.util.Collection;
import java.util.List;

/**
 * 作者:hfj
 * 功能:RAG 可视化知识服务
 * 日期:2026/6/20
 */
public interface RagKnowledgeService {

    /**
     * 保存文档和切块快照。
     *
     * @param document 来源业务文档
     * @param chunkTexts 切块文本
     * @return 已落库的切块
     */
    List<RagChunk> saveDocumentChunks(RagDocumentSource document, List<String> chunkTexts);

    /**
     * 批量标记某类文档删除。
     *
     * @param userId 用户 ID
     * @param documentTypes 文档类型集合
     */
    void markDocumentsDeleted(Long userId, Collection<String> documentTypes);

    /**
     * 标记单个文档删除。
     *
     * @param userId 用户 ID
     * @param documentType 文档类型
     * @param businessId 业务 ID
     */
    void markDocumentDeleted(Long userId, String documentType, Long businessId);

    /**
     * 标记文档和切块已经成功写入向量库。
     */
    void markDocumentIndexed(Long userId, String documentType, Long businessId);

    /**
     * 标记文档写入向量库失败。
     */
    void markDocumentIndexFailed(Long userId, String documentType, Long businessId, String errorMsg);

    /**
     * 对向量召回结果做权限过滤和引用信息补全。
     *
     * @param userId 当前检索用户 ID
     * @param rawResults 原始向量召回结果
     * @param limit 最终返回数量
     * @return 可展示的检索结果
     */
    List<RagSearchResultVO> filterAndEnrichSearchResults(Long userId, List<RagSearchResultVO> rawResults, int limit);

    /**
     * 基于 MySQL 可视化切块做关键词召回。
     *
     * @param userId 当前检索用户 ID
     * @param query 检索词
     * @param limit 最大候选数量
     * @return 已完成权限过滤和引用补全的候选结果
     */
    List<RagSearchResultVO> searchKeywordChunks(Long userId, String query, int limit);

    /**
     * 后台分页查询 RAG 文档。
     */
    IPage<RagDocumentVO> pageDocuments(RagDocumentQueryDTO query);

    /**
     * 后台分页查询 RAG 切块。
     */
    IPage<RagChunkVO> pageChunks(RagChunkQueryDTO query);

    /**
     * 后台查询切块详情。
     */
    RagChunkVO getChunkDetail(Long id);
}
