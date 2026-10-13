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
