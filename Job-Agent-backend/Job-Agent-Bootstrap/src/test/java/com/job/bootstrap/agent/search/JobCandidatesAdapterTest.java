package com.job.bootstrap.agent.search;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * JobCandidatesAdapter 单元测试（第一阶段）。
 *
 * 覆盖重点（文档测试清单 4）:
 * 1. 搜索工具对象结构（total/page/pageSize/jobs）与推荐工具裸数组统一成 JobCandidates；
 * 2. 去重（按 jobId 保序）；
 * 3. 分页/总数无法确定时置空，不编造；
 * 4. 非法 JSON/缺 jobId 的条目被丢弃（不猜）。
 */
class JobCandidatesAdapterTest {

    private final JobCandidatesAdapter adapter = new JobCandidatesAdapter(new ObjectMapper());

    @Test
    void searchResultShouldCarryPagination() {
        JobCandidates candidates = adapter.fromSearchResult("""
                {"total":12,"page":1,"pageSize":5,"jobs":[
                  {"jobId":1,"jobTitle":"Java"},
                  {"jobId":2,"jobTitle":"Go"}
                ]}
                """);

        assertThat(candidates.items()).hasSize(2);
        assertThat(candidates.total()).isEqualTo(12L);
        assertThat(candidates.page()).isEqualTo(1);
        assertThat(candidates.pageSize()).isEqualTo(5);
        assertThat(candidates.hasMore()).isTrue();
        assertThat(candidates.source()).isEqualTo(JobCandidatesAdapter.SOURCE_SEARCH);
    }

    @Test
    void recommendArrayShouldNotInventPagination() {
        JobCandidates candidates = adapter.fromRecommendResult("""
                [{"id":7,"jobTitle":"Java"},{"id":8,"jobTitle":"Go"}]
                """, 1, 5);

        assertThat(candidates.items()).hasSize(2);
        assertThat(candidates.jobIds()).containsExactly(7L, 8L);
        // 推荐工具不返回总数/是否还有更多 → 必须为 null
        assertThat(candidates.total()).isNull();
        assertThat(candidates.hasMore()).isNull();
        assertThat(candidates.source()).isEqualTo(JobCandidatesAdapter.SOURCE_RECOMMEND);
    }

    @Test
    void mergeShouldDedupeKeepingFirstOccurrence() {
        JobCandidates first = adapter.fromSearchResult("""
                {"total":2,"page":1,"pageSize":5,"jobs":[{"jobId":1,"jobTitle":"A"},{"jobId":2,"jobTitle":"B"}]}
                """);
        JobCandidates second = adapter.fromRecommendResult("""
                [{"id":2,"jobTitle":"B2"},{"id":3,"jobTitle":"C"}]
                """, null, null);

        JobCandidates merged = adapter.merge(first, second);

        assertThat(merged.jobIds()).containsExactly(1L, 2L, 3L);
        // 去重保留首次出现的内容（A 版本不动）
        assertThat(merged.items().get(1).raw().get("jobTitle")).isEqualTo("B");
    }

    @Test
    void invalidJsonShouldBeEmpty() {
        assertThat(adapter.fromSearchResult("not-json").isEmpty()).isTrue();
        assertThat(adapter.fromRecommendResult(null, null, null).isEmpty()).isTrue();
    }

    @Test
    void itemWithoutJobIdShouldBeDropped() {
        JobCandidates candidates = adapter.fromSearchResult("""
                {"total":2,"jobs":[{"jobTitle":"无ID"},{"jobId":9,"jobTitle":"有效"}]}
                """);

        assertThat(candidates.jobIds()).containsExactly(9L);
        assertThat(candidates.hasMore()).isNull(); // page/pageSize 缺失 → 不推断
    }
}
