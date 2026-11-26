package com.job.bootstrap.rag.service.impl;

import com.job.bootstrap.rag.config.RagProperties;
import com.job.bootstrap.rag.service.RagEmbeddingService;
import com.job.bootstrap.rag.service.RagKnowledgeService;
import com.job.bootstrap.rag.service.RagVectorStoreService;
import com.job.common.vo.rag.RagSearchResultVO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * RagRetrievalServiceImpl 单元测试（RAG 接入主链路 · 任务2）。
 *
 * 覆盖重点:
 * 1. 混合召回：向量召回 + 关键词召回合并，rerank 排序，referenceNo 连续编号。
 * 2. 权限过滤委托：filterAndEnrichSearchResults 被调用（按 userId 过滤公共+私有知识）。
 * 3. limit 生效：最终返回不超过 limit。
 * 4. 参数校验：userId 为空、query 为空抛 IllegalArgumentException。
 */
class RagRetrievalServiceTest {

    private final RagEmbeddingService ragEmbeddingService = mock(RagEmbeddingService.class);
    private final RagVectorStoreService ragVectorStoreService = mock(RagVectorStoreService.class);
    private final RagKnowledgeService ragKnowledgeService = mock(RagKnowledgeService.class);
    private final RagProperties ragProperties = new RagProperties();

    private final RagRetrievalServiceImpl service = new RagRetrievalServiceImpl(
            ragEmbeddingService, ragVectorStoreService, ragKnowledgeService, ragProperties);

    @Test
    void shouldMergeVectorAndKeywordThenRerank() {
        RagSearchResultVO chunkA = vo(1L, 0L, "RESUME", 10L, 0, "简历A", "Java 后端内容");
        chunkA.setVectorScore(0.9D);
        RagSearchResultVO chunkB = vo(2L, 0L, "JOB", 20L, 0, "岗位B", "Java 岗位描述");

        when(ragEmbeddingService.embed(anyString())).thenReturn(new float[]{0.1f, 0.2f});
        when(ragVectorStoreService.search(anyLong(), any(), anyInt(), anyDouble())).thenReturn(List.of(chunkA));
        when(ragKnowledgeService.filterAndEnrichSearchResults(anyLong(), any(), anyInt())).thenReturn(List.of(chunkA));
        when(ragKnowledgeService.searchKeywordChunks(anyLong(), anyString(), anyInt())).thenReturn(List.of(chunkB));

        List<RagSearchResultVO> results = service.search(100L, "Java", 5);

        assertThat(results).hasSize(2);
        assertThat(results).extracting(RagSearchResultVO::getTitle).containsExactlyInAnyOrder("简历A", "岗位B");
        // 向量命中的 chunkA rerank 分更高，排在第一
        assertThat(results.get(0).getTitle()).isEqualTo("简历A");
        assertThat(results.get(0).getRerankScore()).isGreaterThanOrEqualTo(results.get(1).getRerankScore());
        // referenceNo 从 1 开始连续编号
        assertThat(results).extracting(RagSearchResultVO::getReferenceNo).contains(1, 2);
        // 权限过滤/富化被委托给 RagKnowledgeService
        verify(ragKnowledgeService).filterAndEnrichSearchResults(eq(100L), any(), anyInt());
    }

    @Test
    void shouldRespectLimit() {
        RagSearchResultVO a = vo(1L, 0L, "RESUME", 10L, 0, "A", "x");
        RagSearchResultVO b = vo(2L, 0L, "JOB", 20L, 0, "B", "y");
        when(ragEmbeddingService.embed(anyString())).thenReturn(new float[]{0.1f});
        when(ragVectorStoreService.search(anyLong(), any(), anyInt(), anyDouble())).thenReturn(List.of(a, b));
        when(ragKnowledgeService.filterAndEnrichSearchResults(anyLong(), any(), anyInt())).thenReturn(List.of(a, b));
        when(ragKnowledgeService.searchKeywordChunks(anyLong(), anyString(), anyInt())).thenReturn(List.of());

        List<RagSearchResultVO> results = service.search(100L, "q", 1);

        assertThat(results).hasSize(1);
    }

    @Test
    void nullUserIdShouldThrow() {
        assertThatThrownBy(() -> service.search(null, "q", 5)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void blankQueryShouldThrow() {
        assertThatThrownBy(() -> service.search(100L, "  ", 5)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void crossScoreShouldDriveOrderWhenRuleTied() {
        // query 与两篇文档都无关键词命中 → 规则分打平时，靠交叉分（余弦）定胜负
        RagSearchResultVO a = vo(1L, 0L, "RESUME", 10L, 0, "Alpha", "aaaa");
        RagSearchResultVO b = vo(2L, 0L, "JOB", 20L, 0, "Beta", "bbbb");
        when(ragVectorStoreService.search(anyLong(), any(), anyInt(), anyDouble())).thenReturn(List.of());
        when(ragKnowledgeService.filterAndEnrichSearchResults(anyLong(), any(), anyInt())).thenReturn(List.of());
        when(ragKnowledgeService.searchKeywordChunks(anyLong(), anyString(), anyInt())).thenReturn(List.of(a, b));
        // query 向量 [1,0]；文档 Alpha 向量 [1,0]（余弦 1.0），文档 Beta 向量 [0,1]（余弦 0）
        when(ragEmbeddingService.embed(anyString())).thenAnswer(invocation -> {
            String text = invocation.getArgument(0);
            return text.contains("Beta") ? new float[]{0f, 1f} : new float[]{1f, 0f};
        });

        List<RagSearchResultVO> results = service.search(100L, "xyz", 5);

        assertThat(results).hasSize(2);
        assertThat(results.get(0).getTitle()).isEqualTo("Alpha");
        assertThat(results.get(0).getCrossScore()).isGreaterThan(results.get(1).getCrossScore());
    }

    @Test
    void docEmbedFailureShouldFallbackToRuleScore() {
        RagSearchResultVO a = vo(1L, 0L, "RESUME", 10L, 0, "Alpha", "aaaa");
        RagSearchResultVO b = vo(2L, 0L, "JOB", 20L, 0, "Beta", "bbbb");
        when(ragVectorStoreService.search(anyLong(), any(), anyInt(), anyDouble())).thenReturn(List.of());
        when(ragKnowledgeService.filterAndEnrichSearchResults(anyLong(), any(), anyInt())).thenReturn(List.of());
        when(ragKnowledgeService.searchKeywordChunks(anyLong(), anyString(), anyInt())).thenReturn(List.of(a, b));
        // query 向量化成功，文档向量化抛异常 → 交叉分退化为 0，不影响整体排序
        when(ragEmbeddingService.embed(anyString())).thenAnswer(invocation -> {
            String text = invocation.getArgument(0);
            if (text.contains("Alpha") || text.contains("Beta")) {
                throw new RuntimeException("embed 挂了");
            }
            return new float[]{1f, 0f};
        });

        List<RagSearchResultVO> results = service.search(100L, "xyz", 5);

        assertThat(results).hasSize(2);
        assertThat(results).allMatch(r -> r.getCrossScore() == 0D);
    }

    @Test
    void crossScoreDisabledShouldNotEmbedDocs() {
        ragProperties.getRetrieval().setCrossScoreEnabled(false);
        RagSearchResultVO a = vo(1L, 0L, "RESUME", 10L, 0, "Alpha", "aaaa");
        when(ragVectorStoreService.search(anyLong(), any(), anyInt(), anyDouble())).thenReturn(List.of());
        when(ragKnowledgeService.filterAndEnrichSearchResults(anyLong(), any(), anyInt())).thenReturn(List.of());
        when(ragKnowledgeService.searchKeywordChunks(anyLong(), anyString(), anyInt())).thenReturn(List.of(a));
        when(ragEmbeddingService.embed(anyString())).thenReturn(new float[]{1f, 0f});

        service.search(100L, "xyz", 5);

        // 关闭交叉打分后，只向量化 query 一次，不再向量化文档
        verify(ragEmbeddingService, times(1)).embed(anyString());
    }

    private RagSearchResultVO vo(Long id, Long userId, String docType, Long bizId, int chunkIndex, String title, String content) {
        RagSearchResultVO v = new RagSearchResultVO();
        v.setId(id);
        v.setUserId(userId);
        v.setDocumentType(docType);
        v.setBusinessId(bizId);
        v.setChunkIndex(chunkIndex);
        v.setTitle(title);
        v.setContent(content);
        return v;
    }
}
