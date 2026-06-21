package com.job.bootstrap.rag.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.job.bootstrap.mapper.RagChunkMapper;
import com.job.bootstrap.mapper.RagDocumentMapper;
import com.job.bootstrap.rag.model.RagDocumentSource;
import com.job.bootstrap.rag.service.RagKnowledgeService;
import com.job.common.dto.rag.RagChunkQueryDTO;
import com.job.common.dto.rag.RagDocumentQueryDTO;
import com.job.common.entity.rag.RagChunk;
import com.job.common.entity.rag.RagDocument;
import com.job.common.vo.rag.RagChunkVO;
import com.job.common.vo.rag.RagDocumentVO;
import com.job.common.vo.rag.RagSearchResultVO;
import com.job.enums.RagIndexStatus;
import com.job.enums.RagKnowledgeStatus;
import com.job.enums.RagPermissionScope;
import com.job.exception.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 作者:hfj
 * 功能:RAG 可视化知识服务实现
 * 日期:2026/6/20
 */
@Service
@RequiredArgsConstructor
public class RagKnowledgeServiceImpl implements RagKnowledgeService {

    private static final long PUBLIC_USER_ID = 0L;
    private static final int NOT_DELETED = 0;
    private static final int DELETED = 1;
    private static final int DEFAULT_PAGE_NUM = 1;
    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final int MAX_PAGE_SIZE = 100;
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final RagDocumentMapper ragDocumentMapper;
    private final RagChunkMapper ragChunkMapper;
    private final ObjectMapper objectMapper;

    /**
     * 保存 RAG 文档和切块。
     *
     * 方法步骤:
     * 1. 根据 userId、documentType、businessId 找到同一来源文档。
     * 2. 文档不存在就新增，存在就覆盖标题、hash、权限、元数据和索引状态。
     * 3. 删除旧切块，再写入本次切出来的新切块。
     * 4. 新切块先标记为 PENDING，等 pgvector 写入成功后由索引服务更新为 INDEXED。
     *
     * 说明:
     * - rag_document/rag_chunk 是主库可视化层，可以让 admin 看到实际切块。
     * - pgvector 是向量层，只负责相似度检索。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<RagChunk> saveDocumentChunks(RagDocumentSource document, List<String> chunkTexts) {
        if (document == null || document.getUserId() == null || document.getDocumentType() == null
                || document.getBusinessId() == null) {
            throw new BizException("RAG 文档缺少必要来源信息");
        }
        if (CollectionUtils.isEmpty(chunkTexts)) {
            return List.of();
        }

        Date now = new Date();
        RagDocument savedDocument = saveOrUpdateDocument(document, chunkTexts.size(), now);

        /*
         * 切块是可再生索引数据。
         * 同一文档重新索引时，直接删除旧切块再写新切块，避免旧 chunkIndex 和新 chunkIndex 混在一起。
         */
        ragChunkMapper.delete(new LambdaQueryWrapper<RagChunk>()
                .eq(RagChunk::getDocumentId, savedDocument.getId()));

        List<RagChunk> chunks = new ArrayList<>();
        for (int i = 0; i < chunkTexts.size(); i++) {
            String text = chunkTexts.get(i);
            if (!StringUtils.hasText(text)) {
                continue;
            }

            RagChunk chunk = new RagChunk();
            chunk.setDocumentId(savedDocument.getId());
            chunk.setUserId(document.getUserId());
            chunk.setDocumentType(document.getDocumentType().name());
            chunk.setBusinessId(document.getBusinessId());
            chunk.setChunkIndex(i);
            chunk.setTitle(document.getTitle());
            chunk.setContent(text);
            chunk.setContentHash(sha256(text));
            chunk.setSource(document.getSource());
            chunk.setMetadataJson(toJson(document.getMetadata()));
            chunk.setStatus(RagKnowledgeStatus.ACTIVE.name());
            chunk.setVectorStatus(RagIndexStatus.PENDING.name());
            chunk.setLastIndexTime(now);
            chunk.setIsDeleted(NOT_DELETED);
            chunk.setCreateTime(now);
            chunk.setUpdateTime(now);
            ragChunkMapper.insert(chunk);
            chunks.add(chunk);
        }

        return chunks;
    }

    /**
     * 批量标记文档删除。
     *
     * 该方法用于全量重建前先让旧知识失效，避免旧切块继续被 admin 误认为有效。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markDocumentsDeleted(Long userId, Collection<String> documentTypes) {
        if (userId == null || CollectionUtils.isEmpty(documentTypes)) {
            return;
        }

        List<RagDocument> documents = ragDocumentMapper.selectList(new LambdaQueryWrapper<RagDocument>()
                .eq(RagDocument::getUserId, userId)
                .in(RagDocument::getDocumentType, documentTypes)
                .eq(RagDocument::getIsDeleted, NOT_DELETED));
        for (RagDocument document : documents) {
            markDocumentDeleted(document);
        }
    }

    /**
     * 标记单个文档删除。
     *
     * 删除同步分两层:
     * - 本方法负责 MySQL 可视化层失效。
     * - RagIndexService 负责同时删除 pgvector 向量层。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markDocumentDeleted(Long userId, String documentType, Long businessId) {
        if (userId == null || !StringUtils.hasText(documentType) || businessId == null) {
            return;
        }

        RagDocument document = ragDocumentMapper.selectOne(new LambdaQueryWrapper<RagDocument>()
                .eq(RagDocument::getUserId, userId)
                .eq(RagDocument::getDocumentType, documentType.trim())
                .eq(RagDocument::getBusinessId, businessId)
                .last("LIMIT 1"));
        if (document != null) {
            markDocumentDeleted(document);
        }
    }

    /**
     * 标记文档和切块已经写入向量库。
     *
     * 方法步骤:
     * 1. 找到当前来源文档。
     * 2. 把文档 indexStatus 更新为 INDEXED。
     * 3. 把该文档下的 ACTIVE 切块 vectorStatus 更新为 INDEXED。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markDocumentIndexed(Long userId, String documentType, Long businessId) {
        RagDocument document = findDocument(userId, documentType, businessId);
        if (document == null) {
            return;
        }

        Date now = new Date();
        document.setIndexStatus(RagIndexStatus.INDEXED.name());
        document.setErrorMsg(null);
        document.setLastIndexTime(now);
        document.setUpdateTime(now);
        ragDocumentMapper.updateById(document);

        List<RagChunk> chunks = ragChunkMapper.selectList(new LambdaQueryWrapper<RagChunk>()
                .eq(RagChunk::getDocumentId, document.getId())
                .eq(RagChunk::getStatus, RagKnowledgeStatus.ACTIVE.name())
                .eq(RagChunk::getIsDeleted, NOT_DELETED));
        for (RagChunk chunk : chunks) {
            chunk.setVectorStatus(RagIndexStatus.INDEXED.name());
            chunk.setLastIndexTime(now);
            chunk.setUpdateTime(now);
            ragChunkMapper.updateById(chunk);
        }
    }

    /**
     * 标记文档向量索引失败。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markDocumentIndexFailed(Long userId, String documentType, Long businessId, String errorMsg) {
        RagDocument document = findDocument(userId, documentType, businessId);
        if (document == null) {
            return;
        }

        Date now = new Date();
        document.setIndexStatus(RagIndexStatus.FAILED.name());
        document.setErrorMsg(errorMsg);
        document.setLastIndexTime(now);
        document.setUpdateTime(now);
        ragDocumentMapper.updateById(document);

        List<RagChunk> chunks = ragChunkMapper.selectList(new LambdaQueryWrapper<RagChunk>()
                .eq(RagChunk::getDocumentId, document.getId())
                .eq(RagChunk::getIsDeleted, NOT_DELETED));
        for (RagChunk chunk : chunks) {
            chunk.setVectorStatus(RagIndexStatus.FAILED.name());
            chunk.setUpdateTime(now);
            ragChunkMapper.updateById(chunk);
        }
    }

    /**
     * 过滤并补全向量召回结果。
     *
     * 方法步骤:
     * 1. 优先从向量 metadata 里读取 chunkId。
     * 2. 找不到 chunkId 时，用 userId、documentType、businessId、chunkIndex 回查 MySQL 切块。
     * 3. 校验文档和切块必须 ACTIVE 且未删除。
     * 4. 校验当前用户只能读取公共知识或自己的私有知识。
     * 5. 用 MySQL 切块正文覆盖向量结果里的 content，保证展示内容来自可视化主库。
     */
    @Override
    public List<RagSearchResultVO> filterAndEnrichSearchResults(
            Long userId,
            List<RagSearchResultVO> rawResults,
            int limit
    ) {
        if (CollectionUtils.isEmpty(rawResults) || userId == null) {
            return List.of();
        }

        List<RagSearchResultVO> results = new ArrayList<>();
        for (RagSearchResultVO rawResult : rawResults) {
            if (results.size() >= limit) {
                break;
            }

            RagChunk chunk = findMatchedChunk(rawResult);
            if (chunk == null || !canRead(userId, chunk.getUserId())) {
                continue;
            }

            RagDocument document = ragDocumentMapper.selectById(chunk.getDocumentId());
            if (!isActive(document) || !isActive(chunk)) {
                continue;
            }

            enrichResult(rawResult, document, chunk, results.size() + 1);
            results.add(rawResult);
        }
        return results;
    }

    /**
     * 基于 MySQL 切块做关键词召回。
     *
     * 方法步骤:
     * 1. 从用户问题中提取少量关键词，避免一条 SQL 拼出过多 LIKE 条件。
     * 2. 只查询公共知识和当前用户自己的私有知识。
     * 3. 只召回 ACTIVE 且已经 INDEXED 的切块，避免失败或待索引数据进入 Agent 上下文。
     * 4. 回查文档状态后复用 enrichResult 补齐引用信息。
     */
    @Override
    public List<RagSearchResultVO> searchKeywordChunks(Long userId, String query, int limit) {
        if (userId == null || !StringUtils.hasText(query) || limit <= 0) {
            return List.of();
        }

        List<String> keywords = extractKeywords(query);
        if (CollectionUtils.isEmpty(keywords)) {
            return List.of();
        }

        int candidateLimit = normalizeCandidateLimit(limit);
        LambdaQueryWrapper<RagChunk> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(RagChunk::getUserId, List.of(PUBLIC_USER_ID, userId))
                .eq(RagChunk::getStatus, RagKnowledgeStatus.ACTIVE.name())
                .eq(RagChunk::getVectorStatus, RagIndexStatus.INDEXED.name())
                .eq(RagChunk::getIsDeleted, NOT_DELETED)
                .and(group -> {
                    for (int i = 0; i < keywords.size(); i++) {
                        String keyword = keywords.get(i);
                        if (i > 0) {
                            group.or();
                        }
                        group.like(RagChunk::getTitle, keyword)
                                .or()
                                .like(RagChunk::getContent, keyword)
                                .or()
                                .like(RagChunk::getMetadataJson, keyword);
                    }
                })
                .orderByDesc(RagChunk::getUpdateTime)
                .last("LIMIT " + candidateLimit);

        List<RagChunk> chunks = ragChunkMapper.selectList(wrapper);
        if (CollectionUtils.isEmpty(chunks)) {
            return List.of();
        }

        List<RagSearchResultVO> results = new ArrayList<>();
        for (RagChunk chunk : chunks) {
            if (results.size() >= candidateLimit || !canRead(userId, chunk.getUserId())) {
                continue;
            }

            RagDocument document = ragDocumentMapper.selectById(chunk.getDocumentId());
            if (!isActive(document) || !isActive(chunk)) {
                continue;
            }

            RagSearchResultVO result = new RagSearchResultVO();
            result.setRetrievalSource("KEYWORD");
            enrichResult(result, document, chunk, results.size() + 1);
            results.add(result);
        }
        return results;
    }

    /**
     * 后台分页查询 RAG 文档。
     */
    @Override
    public IPage<RagDocumentVO> pageDocuments(RagDocumentQueryDTO query) {
        long pageNum = normalizePageNum(query.getPageNum());
        long pageSize = normalizePageSize(query.getPageSize());

        LambdaQueryWrapper<RagDocument> wrapper = new LambdaQueryWrapper<>();
        if (query.getUserId() != null) {
            wrapper.eq(RagDocument::getUserId, query.getUserId());
        }
        if (StringUtils.hasText(query.getDocumentType())) {
            wrapper.eq(RagDocument::getDocumentType, query.getDocumentType().trim());
        }
        if (query.getBusinessId() != null) {
            wrapper.eq(RagDocument::getBusinessId, query.getBusinessId());
        }
        if (StringUtils.hasText(query.getTitle())) {
            wrapper.like(RagDocument::getTitle, query.getTitle().trim());
        }
        if (StringUtils.hasText(query.getPermissionScope())) {
            wrapper.eq(RagDocument::getPermissionScope, query.getPermissionScope().trim());
        }
        if (StringUtils.hasText(query.getStatus())) {
            wrapper.eq(RagDocument::getStatus, query.getStatus().trim());
        }
        if (StringUtils.hasText(query.getIndexStatus())) {
            wrapper.eq(RagDocument::getIndexStatus, query.getIndexStatus().trim());
        }
        if (StringUtils.hasText(query.getKeyword())) {
            String keyword = query.getKeyword().trim();
            wrapper.and(item -> item
                    .like(RagDocument::getTitle, keyword)
                    .or()
                    .like(RagDocument::getSource, keyword)
                    .or()
                    .like(RagDocument::getMetadataJson, keyword));
        }
        if (StringUtils.hasText(query.getStartTime())) {
            wrapper.ge(RagDocument::getCreateTime, query.getStartTime().trim());
        }
        if (StringUtils.hasText(query.getEndTime())) {
            wrapper.le(RagDocument::getCreateTime, query.getEndTime().trim());
        }

        wrapper.orderByDesc(RagDocument::getUpdateTime);
        return ragDocumentMapper.selectPage(new Page<>(pageNum, pageSize), wrapper).convert(RagDocumentVO::from);
    }

    /**
     * 后台分页查询 RAG 切块。
     */
    @Override
    public IPage<RagChunkVO> pageChunks(RagChunkQueryDTO query) {
        long pageNum = normalizePageNum(query.getPageNum());
        long pageSize = normalizePageSize(query.getPageSize());

        LambdaQueryWrapper<RagChunk> wrapper = new LambdaQueryWrapper<>();
        if (query.getDocumentId() != null) {
            wrapper.eq(RagChunk::getDocumentId, query.getDocumentId());
        }
        if (query.getUserId() != null) {
            wrapper.eq(RagChunk::getUserId, query.getUserId());
        }
        if (StringUtils.hasText(query.getDocumentType())) {
            wrapper.eq(RagChunk::getDocumentType, query.getDocumentType().trim());
        }
        if (query.getBusinessId() != null) {
            wrapper.eq(RagChunk::getBusinessId, query.getBusinessId());
        }
        if (StringUtils.hasText(query.getTitle())) {
            wrapper.like(RagChunk::getTitle, query.getTitle().trim());
        }
        if (StringUtils.hasText(query.getStatus())) {
            wrapper.eq(RagChunk::getStatus, query.getStatus().trim());
        }
        if (StringUtils.hasText(query.getVectorStatus())) {
            wrapper.eq(RagChunk::getVectorStatus, query.getVectorStatus().trim());
        }
        if (StringUtils.hasText(query.getKeyword())) {
            String keyword = query.getKeyword().trim();
            wrapper.and(item -> item
                    .like(RagChunk::getTitle, keyword)
                    .or()
                    .like(RagChunk::getContent, keyword)
                    .or()
                    .like(RagChunk::getMetadataJson, keyword));
        }
        if (StringUtils.hasText(query.getStartTime())) {
            wrapper.ge(RagChunk::getCreateTime, query.getStartTime().trim());
        }
        if (StringUtils.hasText(query.getEndTime())) {
            wrapper.le(RagChunk::getCreateTime, query.getEndTime().trim());
        }

        wrapper.orderByDesc(RagChunk::getUpdateTime)
                .orderByAsc(RagChunk::getChunkIndex);
        return ragChunkMapper.selectPage(new Page<>(pageNum, pageSize), wrapper).convert(RagChunkVO::from);
    }

    /**
     * 后台查询切块详情。
     */
    @Override
    public RagChunkVO getChunkDetail(Long id) {
        RagChunk chunk = ragChunkMapper.selectById(id);
        if (chunk == null) {
            throw new BizException("RAG 切块不存在");
        }
        return RagChunkVO.from(chunk);
    }

    private RagDocument saveOrUpdateDocument(RagDocumentSource source, int chunkCount, Date now) {
        RagDocument document = ragDocumentMapper.selectOne(new LambdaQueryWrapper<RagDocument>()
                .eq(RagDocument::getUserId, source.getUserId())
                .eq(RagDocument::getDocumentType, source.getDocumentType().name())
                .eq(RagDocument::getBusinessId, source.getBusinessId())
                .last("LIMIT 1"));
        boolean insert = document == null;
        if (insert) {
            document = new RagDocument();
            document.setUserId(source.getUserId());
            document.setDocumentType(source.getDocumentType().name());
            document.setBusinessId(source.getBusinessId());
            document.setCreateTime(now);
        }

        document.setTitle(source.getTitle());
        document.setSource(source.getSource());
        document.setPermissionScope(resolvePermissionScope(source.getUserId()));
        document.setContentHash(sha256(source.getContent()));
        document.setChunkCount(chunkCount);
        document.setStatus(RagKnowledgeStatus.ACTIVE.name());
        document.setIndexStatus(RagIndexStatus.PENDING.name());
        document.setErrorMsg(null);
        document.setLastIndexTime(now);
        document.setMetadataJson(toJson(source.getMetadata()));
        document.setIsDeleted(NOT_DELETED);
        document.setUpdateTime(now);

        if (insert) {
            ragDocumentMapper.insert(document);
        } else {
            ragDocumentMapper.updateById(document);
        }
        return document;
    }

    private RagDocument findDocument(Long userId, String documentType, Long businessId) {
        if (userId == null || !StringUtils.hasText(documentType) || businessId == null) {
            return null;
        }

        return ragDocumentMapper.selectOne(new LambdaQueryWrapper<RagDocument>()
                .eq(RagDocument::getUserId, userId)
                .eq(RagDocument::getDocumentType, documentType.trim())
                .eq(RagDocument::getBusinessId, businessId)
                .last("LIMIT 1"));
    }

    private void markDocumentDeleted(RagDocument document) {
        Date now = new Date();
        document.setStatus(RagKnowledgeStatus.DELETED.name());
        document.setIndexStatus(RagIndexStatus.PENDING.name());
        document.setIsDeleted(DELETED);
        document.setUpdateTime(now);
        ragDocumentMapper.updateById(document);

        List<RagChunk> chunks = ragChunkMapper.selectList(new LambdaQueryWrapper<RagChunk>()
                .eq(RagChunk::getDocumentId, document.getId())
                .eq(RagChunk::getIsDeleted, NOT_DELETED));
        for (RagChunk chunk : chunks) {
            chunk.setStatus(RagKnowledgeStatus.DELETED.name());
            chunk.setVectorStatus(RagIndexStatus.PENDING.name());
            chunk.setIsDeleted(DELETED);
            chunk.setUpdateTime(now);
            ragChunkMapper.updateById(chunk);
        }
    }

    private RagChunk findMatchedChunk(RagSearchResultVO result) {
        Long chunkId = result.getChunkId();
        if (chunkId == null) {
            chunkId = getLong(result.getMetadata(), "chunkId");
        }
        if (chunkId != null) {
            return ragChunkMapper.selectById(chunkId);
        }

        return ragChunkMapper.selectOne(new LambdaQueryWrapper<RagChunk>()
                .eq(RagChunk::getUserId, result.getUserId())
                .eq(RagChunk::getDocumentType, result.getDocumentType())
                .eq(RagChunk::getBusinessId, result.getBusinessId())
                .eq(RagChunk::getChunkIndex, result.getChunkIndex())
                .last("LIMIT 1"));
    }

    private void enrichResult(RagSearchResultVO result, RagDocument document, RagChunk chunk, int referenceNo) {
        Map<String, Object> metadata = readJsonMap(chunk.getMetadataJson());
        metadata.put("documentId", document.getId());
        metadata.put("chunkId", chunk.getId());

        result.setDocumentId(document.getId());
        result.setChunkId(chunk.getId());
        result.setReferenceNo(referenceNo);
        result.setUserId(chunk.getUserId());
        result.setDocumentType(chunk.getDocumentType());
        result.setBusinessId(chunk.getBusinessId());
        result.setChunkIndex(chunk.getChunkIndex());
        result.setTitle(chunk.getTitle());
        result.setContent(chunk.getContent());
        result.setSource(chunk.getSource());
        result.setPermissionScope(document.getPermissionScope());
        result.setReferenceTitle(buildReferenceTitle(referenceNo, document, chunk));
        result.setMetadata(metadata);
    }

    private String buildReferenceTitle(int referenceNo, RagDocument document, RagChunk chunk) {
        return "引用" + referenceNo + ": "
                + nullToDash(document.getDocumentType())
                + "/"
                + nullToDash(document.getTitle())
                + "#"
                + chunk.getChunkIndex();
    }

    private boolean canRead(Long requestUserId, Long knowledgeUserId) {
        if (knowledgeUserId == null) {
            return false;
        }
        return knowledgeUserId.equals(PUBLIC_USER_ID) || knowledgeUserId.equals(requestUserId);
    }

    private boolean isActive(RagDocument document) {
        return document != null
                && Integer.valueOf(NOT_DELETED).equals(document.getIsDeleted())
                && RagKnowledgeStatus.ACTIVE.name().equals(document.getStatus());
    }

    private boolean isActive(RagChunk chunk) {
        return chunk != null
                && Integer.valueOf(NOT_DELETED).equals(chunk.getIsDeleted())
                && RagKnowledgeStatus.ACTIVE.name().equals(chunk.getStatus());
    }

    private String resolvePermissionScope(Long userId) {
        return userId != null && userId.equals(PUBLIC_USER_ID)
                ? RagPermissionScope.PUBLIC.name()
                : RagPermissionScope.PRIVATE.name();
    }

    private long normalizePageNum(Long pageNum) {
        return pageNum == null || pageNum <= 0 ? DEFAULT_PAGE_NUM : pageNum;
    }

    private long normalizePageSize(Long pageSize) {
        if (pageSize == null || pageSize <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }

    private int normalizeCandidateLimit(int limit) {
        if (limit <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(limit, 50);
    }

    private List<String> extractKeywords(String query) {
        if (!StringUtils.hasText(query)) {
            return List.of();
        }

        /*
         * 关键词召回不是分词引擎，第一版只做轻量拆词:
         * - 保留原始短查询，适合后台直接搜岗位名、公司名、技能词。
         * - 再按空格和常见标点拆出英文/数字/中文片段。
         * - 最多保留 5 个词，避免 LIKE 条件过多影响后台检索。
         */
        Set<String> keywords = new LinkedHashSet<>();
        String trimmed = query.trim();
        if (trimmed.length() <= 60) {
            keywords.add(trimmed);
        }

        String normalized = trimmed.replaceAll("[\\s\\p{Punct}，。；：！？、（）【】《》]+", " ");
        for (String part : normalized.split(" ")) {
            String keyword = part.trim();
            if (keyword.length() >= 2) {
                keywords.add(keyword);
            }
            if (keywords.size() >= 5) {
                break;
            }
        }
        return new ArrayList<>(keywords);
    }

    private Long getLong(Map<String, Object> metadata, String key) {
        if (metadata == null || !metadata.containsKey(key)) {
            return null;
        }
        Object value = metadata.get(key);
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && StringUtils.hasText(text)) {
            return Long.valueOf(text.trim());
        }
        return null;
    }

    private Map<String, Object> readJsonMap(String json) {
        if (!StringUtils.hasText(json)) {
            return new LinkedHashMap<>();
        }
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (Exception exception) {
            return new LinkedHashMap<>();
        }
    }

    private String toJson(Map<String, Object> metadata) {
        try {
            return objectMapper.writeValueAsString(metadata == null ? Map.of() : metadata);
        } catch (Exception exception) {
            return "{}";
        }
    }

    private String nullToDash(Object value) {
        return value == null ? "-" : String.valueOf(value);
    }

    private String sha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((text == null ? "" : text).getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte value : hash) {
                builder.append(String.format("%02x", value));
            }
            return builder.toString();
        } catch (Exception exception) {
            throw new IllegalStateException("计算 RAG 文本哈希失败", exception);
        }
    }
}
