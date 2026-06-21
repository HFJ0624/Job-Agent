package com.job.bootstrap.rag.service.impl;

import com.job.bootstrap.rag.config.RagProperties;
import com.job.bootstrap.rag.service.RagEmbeddingService;
import com.job.bootstrap.rag.service.RagKnowledgeService;
import com.job.bootstrap.rag.service.RagRetrievalService;
import com.job.bootstrap.rag.service.RagVectorStoreService;
import com.job.common.dto.rag.RagRetrievalEvalDTO;
import com.job.common.vo.rag.RagRetrievalEvalVO;
import com.job.common.vo.rag.RagSearchResultVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

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
    private final RagKnowledgeService ragKnowledgeService;
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

        int candidateLimit = rawCandidateLimit(actualLimit);

        /*
         * 混合检索第一路: 向量召回。
         * 1. 先把用户问题转成向量。
         * 2. 再用同一个向量空间去 pgvector 中做 cosine 相似度检索。
         * 3. 检索范围包含 user_id=0 的公共知识和当前 userId 的私有知识。
         */
        float[] queryVector = ragEmbeddingService.embed(query.trim());
        List<RagSearchResultVO> rawVectorResults = ragVectorStoreService.search(
                userId,
                queryVector,
                candidateLimit,
                ragProperties.getRetrieval().getMinScore()
        );
        List<RagSearchResultVO> vectorResults = ragKnowledgeService.filterAndEnrichSearchResults(
                userId,
                rawVectorResults,
                candidateLimit
        );

        /*
         * 混合检索第二路: 关键词召回。
         * 这一路解决“岗位名、公司名、技能缩写”等精确词没有被向量召回覆盖的问题。
         */
        List<RagSearchResultVO> keywordResults = ragKnowledgeService.searchKeywordChunks(
                userId,
                query.trim(),
                candidateLimit
        );

        return mergeAndRerank(query.trim(), vectorResults, keywordResults, actualLimit);
    }

    /**
     * 后台评测单条 query 的召回质量。
     *
     * 方法步骤:
     * 1. 复用线上同一套 search 方法，避免评测链路和真实链路不一致。
     * 2. 按后台传入的期望 chunk、document、业务来源或关键词判断是否命中。
     * 3. 返回 hitRank 和 reciprocalRank，后续可以扩展为批量评测集。
     */
    @Override
    public RagRetrievalEvalVO evaluate(RagRetrievalEvalDTO query) {
        if (query == null) {
            throw new IllegalArgumentException("评测参数不能为空");
        }

        Long userId = query.getUserId() == null ? 0L : query.getUserId();
        int actualLimit = resolveLimit(query.getLimit());
        List<RagSearchResultVO> results = search(userId, query.getQuery(), actualLimit);
        Integer hitRank = findHitRank(query, results);

        RagRetrievalEvalVO vo = new RagRetrievalEvalVO();
        vo.setUserId(userId);
        vo.setQuery(query.getQuery());
        vo.setLimit(actualLimit);
        vo.setRetrievedCount(results.size());
        vo.setHit(hitRank != null);
        vo.setHitRank(hitRank);
        vo.setReciprocalRank(hitRank == null ? 0D : 1D / hitRank);
        vo.setExpectedTarget(buildExpectedTarget(query));
        vo.setMessage(buildEvaluateMessage(hitRank, vo.getExpectedTarget()));
        vo.setResults(results);
        return vo;
    }

    private List<RagSearchResultVO> mergeAndRerank(
            String query,
            List<RagSearchResultVO> vectorResults,
            List<RagSearchResultVO> keywordResults,
            int limit
    ) {
        Map<String, SearchCandidate> candidates = new LinkedHashMap<>();
        addVectorCandidates(candidates, vectorResults);
        addKeywordCandidates(candidates, keywordResults);

        List<RagSearchResultVO> reranked = new ArrayList<>();
        for (SearchCandidate candidate : candidates.values()) {
            if (candidate.result == null) {
                continue;
            }

            /*
             * 重排第一版采用确定性规则:
             * 1. vectorScore 表示语义相似度。
             * 2. keywordScore 表示标题、正文、元数据的精确词匹配。
             * 3. 同一 chunk 同时被两路命中时给少量加成，说明它兼具语义相关和字面相关。
             */
            RagSearchResultVO result = candidate.result;
            double keywordScore = calculateKeywordScore(query, result);
            if (candidate.keywordHit) {
                keywordScore = Math.max(keywordScore, 0.4D);
            }

            double vectorScore = candidate.vectorScore == null ? 0D : clamp(candidate.vectorScore);
            double semanticScore = vectorScore > 0D ? vectorScore : keywordScore * 0.85D;
            boolean hybridHit = vectorScore > 0D && candidate.keywordHit;
            double rerankScore = clamp(semanticScore * 0.65D + keywordScore * 0.35D + (hybridHit ? 0.08D : 0D));

            result.setVectorScore(vectorScore);
            result.setKeywordScore(keywordScore);
            result.setRerankScore(rerankScore);
            result.setScore(rerankScore);
            result.setRetrievalSource(hybridHit ? "HYBRID" : (vectorScore > 0D ? "VECTOR" : "KEYWORD"));
            reranked.add(result);
        }

        reranked.sort(Comparator.comparingDouble(
                (RagSearchResultVO result) -> defaultScore(result.getRerankScore())
        ).reversed());

        List<RagSearchResultVO> results = new ArrayList<>();
        for (RagSearchResultVO result : reranked) {
            if (results.size() >= limit) {
                break;
            }
            result.setReferenceNo(results.size() + 1);
            result.setReferenceTitle(rewriteReferenceTitle(result.getReferenceTitle(), result.getReferenceNo()));
            results.add(result);
        }
        return results;
    }

    private void addVectorCandidates(Map<String, SearchCandidate> candidates, List<RagSearchResultVO> results) {
        if (results == null) {
            return;
        }
        for (RagSearchResultVO result : results) {
            SearchCandidate candidate = candidates.computeIfAbsent(candidateKey(result), key -> new SearchCandidate());
            candidate.result = result;
            candidate.vectorScore = result.getVectorScore() == null ? result.getScore() : result.getVectorScore();
        }
    }

    private void addKeywordCandidates(Map<String, SearchCandidate> candidates, List<RagSearchResultVO> results) {
        if (results == null) {
            return;
        }
        for (RagSearchResultVO result : results) {
            SearchCandidate candidate = candidates.computeIfAbsent(candidateKey(result), key -> new SearchCandidate());
            if (candidate.result == null) {
                candidate.result = result;
            }
            candidate.keywordHit = true;
        }
    }

    private String candidateKey(RagSearchResultVO result) {
        if (result.getChunkId() != null) {
            return "chunk:" + result.getChunkId();
        }
        return "source:"
                + result.getUserId()
                + ":"
                + result.getDocumentType()
                + ":"
                + result.getBusinessId()
                + ":"
                + result.getChunkIndex();
    }

    private double calculateKeywordScore(String query, RagSearchResultVO result) {
        List<String> terms = extractTerms(query);
        if (terms.isEmpty()) {
            return 0D;
        }

        String haystack = normalizeText(
                nullToEmpty(result.getTitle())
                        + " "
                        + nullToEmpty(result.getContent())
                        + " "
                        + nullToEmpty(result.getReferenceTitle())
                        + " "
                        + (result.getMetadata() == null ? "" : result.getMetadata().toString())
        );

        int matched = 0;
        for (String term : terms) {
            if (haystack.contains(term)) {
                matched++;
            }
        }

        double score = ((double) matched / terms.size()) * 0.7D;
        String normalizedQuery = normalizeText(query);
        if (normalizedQuery.length() >= 2 && normalizedQuery.length() <= 60 && haystack.contains(normalizedQuery)) {
            score += 0.2D;
        }

        String title = normalizeText(result.getTitle());
        for (String term : terms) {
            if (title.contains(term)) {
                score += 0.1D;
                break;
            }
        }
        return clamp(score);
    }

    private List<String> extractTerms(String query) {
        if (!StringUtils.hasText(query)) {
            return List.of();
        }

        /*
         * 这里不做复杂中文分词，避免第一版引入额外依赖。
         * 保留完整短查询，同时抽取英文/数字片段，能覆盖 Java、React、AI、薪资等常见精确词。
         */
        Map<String, Boolean> terms = new LinkedHashMap<>();
        String normalized = normalizeText(query);
        if (normalized.length() >= 2 && normalized.length() <= 60) {
            terms.put(normalized, Boolean.TRUE);
        }

        String splitText = normalized.replaceAll("[\\s\\p{Punct}，。；：！？、（）【】《》]+", " ");
        for (String part : splitText.split(" ")) {
            addTerm(terms, part);
        }

        String asciiText = normalized.replaceAll("[^a-z0-9]+", " ");
        for (String part : asciiText.split(" ")) {
            addTerm(terms, part);
        }

        List<String> result = new ArrayList<>();
        for (String term : terms.keySet()) {
            result.add(term);
            if (result.size() >= 8) {
                break;
            }
        }
        return result;
    }

    private void addTerm(Map<String, Boolean> terms, String value) {
        String term = normalizeText(value);
        if (term.length() >= 2) {
            terms.put(term, Boolean.TRUE);
        }
    }

    private Integer findHitRank(RagRetrievalEvalDTO query, List<RagSearchResultVO> results) {
        if (!hasExpectedTarget(query)) {
            return null;
        }
        for (int i = 0; i < results.size(); i++) {
            if (matchesExpected(query, results.get(i))) {
                return i + 1;
            }
        }
        return null;
    }

    private boolean matchesExpected(RagRetrievalEvalDTO query, RagSearchResultVO result) {
        if (query.getExpectedChunkId() != null) {
            return Objects.equals(query.getExpectedChunkId(), result.getChunkId());
        }
        if (query.getExpectedDocumentId() != null) {
            return Objects.equals(query.getExpectedDocumentId(), result.getDocumentId());
        }
        if (StringUtils.hasText(query.getExpectedDocumentType()) && query.getExpectedBusinessId() != null) {
            return query.getExpectedDocumentType().trim().equalsIgnoreCase(nullToEmpty(result.getDocumentType()))
                    && Objects.equals(query.getExpectedBusinessId(), result.getBusinessId());
        }
        if (StringUtils.hasText(query.getExpectedKeyword())) {
            String keyword = normalizeText(query.getExpectedKeyword());
            String haystack = normalizeText(
                    nullToEmpty(result.getTitle())
                            + " "
                            + nullToEmpty(result.getContent())
                            + " "
                            + nullToEmpty(result.getReferenceTitle())
            );
            return haystack.contains(keyword);
        }
        return false;
    }

    private boolean hasExpectedTarget(RagRetrievalEvalDTO query) {
        return query.getExpectedChunkId() != null
                || query.getExpectedDocumentId() != null
                || (StringUtils.hasText(query.getExpectedDocumentType()) && query.getExpectedBusinessId() != null)
                || StringUtils.hasText(query.getExpectedKeyword());
    }

    private String buildExpectedTarget(RagRetrievalEvalDTO query) {
        List<String> parts = new ArrayList<>();
        if (query.getExpectedChunkId() != null) {
            parts.add("chunkId=" + query.getExpectedChunkId());
        }
        if (query.getExpectedDocumentId() != null) {
            parts.add("documentId=" + query.getExpectedDocumentId());
        }
        if (StringUtils.hasText(query.getExpectedDocumentType()) && query.getExpectedBusinessId() != null) {
            parts.add(query.getExpectedDocumentType().trim() + "#" + query.getExpectedBusinessId());
        }
        if (StringUtils.hasText(query.getExpectedKeyword())) {
            parts.add("keyword=" + query.getExpectedKeyword().trim());
        }
        return parts.isEmpty() ? "未提供期望目标" : String.join(", ", parts);
    }

    private String buildEvaluateMessage(Integer hitRank, String expectedTarget) {
        if ("未提供期望目标".equals(expectedTarget)) {
            return "未提供期望目标，本次仅返回召回结果用于人工检查";
        }
        return hitRank == null
                ? "未命中期望目标: " + expectedTarget
                : "命中期望目标，排名第 " + hitRank + " 位";
    }

    private String rewriteReferenceTitle(String referenceTitle, Integer referenceNo) {
        if (!StringUtils.hasText(referenceTitle) || referenceNo == null) {
            return referenceTitle;
        }
        int index = referenceTitle.indexOf(":");
        if (index < 0) {
            return "引用" + referenceNo + ": " + referenceTitle;
        }
        return "引用" + referenceNo + referenceTitle.substring(index);
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

    private int rawCandidateLimit(int finalLimit) {
        return Math.min(finalLimit * 3, 50);
    }

    private String normalizeText(String text) {
        return text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private double defaultScore(Double score) {
        return score == null ? 0D : score;
    }

    private double clamp(Double score) {
        if (score == null || score.isNaN() || score.isInfinite()) {
            return 0D;
        }
        return Math.max(0D, Math.min(1D, score));
    }

    private static class SearchCandidate {

        private RagSearchResultVO result;

        private boolean keywordHit;

        private Double vectorScore;
    }
}
