package com.job.bootstrap.agent.cache;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * InMemorySemanticCacheStore 单元测试（任务 G10）。
 *
 * 覆盖重点:
 * 1. 相似度超过阈值命中、低于阈值不命中；
 * 2. TTL 过期条目不命中且被惰性清理；
 * 3. LRU 淘汰（超过单用户容量逐最久未用）；
 * 4. 用户隔离：A 的缓存 B 摸不到；
 * 5. 空/非法输入安全返回。
 */
class InMemorySemanticCacheStoreTest {

    private InMemorySemanticCacheStore store;

    @BeforeEach
    void setUp() {
        AgentCacheProperties properties = new AgentCacheProperties();
        properties.setSimilarityThreshold(0.9);
        properties.setMaxEntriesPerUser(2);
        properties.setTtlMinutes(30);
        store = new InMemorySemanticCacheStore(properties);
    }

    private CacheStore.CacheEntry entry(String question, float[] embedding, long expireAt) {
        return new CacheStore.CacheEntry(question, embedding, "答案:" + question, expireAt);
    }

    @Test
    void similarQueryShouldHit() {
        float[] base = {1f, 0f, 0f};
        store.put(100L, entry("分析简历", base, System.currentTimeMillis() + 60_000));

        // 高度相似的向量（几乎同向）
        Optional<CacheStore.CacheEntry> hit = store.lookup(100L, new float[]{0.99f, 0.1f, 0f}, 0.9);

        assertThat(hit).isPresent();
        assertThat(hit.get().answer()).isEqualTo("答案:分析简历");
    }

    @Test
    void dissimilarQueryShouldMiss() {
        store.put(100L, entry("分析简历", new float[]{1f, 0f, 0f}, System.currentTimeMillis() + 60_000));

        Optional<CacheStore.CacheEntry> hit = store.lookup(100L, new float[]{0f, 1f, 0f}, 0.9);

        assertThat(hit).isEmpty();
    }

    @Test
    void expiredEntryShouldNotHit() {
        store.put(100L, entry("分析简历", new float[]{1f, 0f, 0f}, System.currentTimeMillis() - 1));

        Optional<CacheStore.CacheEntry> hit = store.lookup(100L, new float[]{1f, 0f, 0f}, 0.9);

        assertThat(hit).isEmpty();
    }

    @Test
    void shouldEvictLeastRecentlyUsed() {
        long expireAt = System.currentTimeMillis() + 60_000;
        store.put(100L, entry("问题A", new float[]{1f, 0f, 0f}, expireAt));
        store.put(100L, entry("问题B", new float[]{0f, 1f, 0f}, expireAt));
        // 访问 A，使 B 成为最久未用
        store.lookup(100L, new float[]{1f, 0f, 0f}, 0.9);
        // 写入 C 触发淘汰：B 被逐掉，A 保留
        store.put(100L, entry("问题C", new float[]{0f, 0f, 1f}, expireAt));

        assertThat(store.lookup(100L, new float[]{1f, 0f, 0f}, 0.9)).isPresent();
        assertThat(store.lookup(100L, new float[]{0f, 1f, 0f}, 0.9)).isEmpty();
        assertThat(store.lookup(100L, new float[]{0f, 0f, 1f}, 0.9)).isPresent();
    }

    @Test
    void userIsolationShouldHold() {
        store.put(100L, entry("分析简历", new float[]{1f, 0f, 0f}, System.currentTimeMillis() + 60_000));

        // 另一个用户用完全相同的向量也摸不到
        assertThat(store.lookup(200L, new float[]{1f, 0f, 0f}, 0.9)).isEmpty();
    }

    @Test
    void invalidInputShouldReturnEmpty() {
        assertThat(store.lookup(null, new float[]{1f}, 0.9)).isEmpty();
        assertThat(store.lookup(100L, null, 0.9)).isEmpty();
        assertThat(store.lookup(100L, new float[0], 0.9)).isEmpty();
        store.put(100L, null); // 不炸
        store.put(null, entry("x", new float[]{1f}, System.currentTimeMillis() + 60_000)); // 不炸
    }
}
