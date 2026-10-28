package com.job.bootstrap.agent.cache;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 功能: 进程内语义缓存存储（任务 G10，v1 实现）。
 *
 * 设计说明:
 * 1. 结构：ConcurrentHashMap&lt;userId, LinkedHashMap&lt;question, CacheEntry&gt;&gt;，
 *    内层 LinkedHashMap 按访问序实现 LRU（accessOrder=true），容量超限逐最久未用；
 * 2. 过期：lookup 时惰性清理过期条目；put 时顺带清掉该用户已过期的条目；
 * 3. 相似度：全量余弦（每用户最多 maxEntriesPerUser 条，2048 维向量几千条也是亚毫秒级），
 *    与 pgvector 无索引精确扫描同思路——小数据量全扫比建索引简单可靠；
 * 4. 并发：computeIfAbsent + synchronized(内层Map) 保证同用户读写互斥，
 *    不同用户零竞争；
 * 5. 诚实边界：进程内缓存重启丢失、多实例不共享——单机演示场景可接受，
 *    需要持久化/跨实例时新增 CacheStore 实现（pgvector）即可。
 */
@Slf4j
@Component
public class InMemorySemanticCacheStore implements CacheStore {

    private final Map<Long, LinkedHashMap<String, CacheEntry>> userCaches = new ConcurrentHashMap<>();

    private final AgentCacheProperties properties;

    public InMemorySemanticCacheStore(AgentCacheProperties properties) {
        this.properties = properties;
    }

    @Override
    public Optional<CacheEntry> lookup(Long userId, float[] queryEmbedding, double threshold) {
        if (userId == null || queryEmbedding == null || queryEmbedding.length == 0) {
            return Optional.empty();
        }
        LinkedHashMap<String, CacheEntry> entries = userCaches.get(userId);
        if (entries == null || entries.isEmpty()) {
            return Optional.empty();
        }

        long now = System.currentTimeMillis();
        synchronized (entries) {
            purgeExpired(entries, now);
            CacheEntry best = null;
            double bestScore = threshold;
            for (CacheEntry entry : entries.values()) {
                double score = cosine(queryEmbedding, entry.embedding());
                if (score >= bestScore) {
                    best = entry;
                    bestScore = score;
                }
            }
            if (best == null) {
                return Optional.empty();
            }
            /*
             * 命中后必须走一次 get 刷新 LRU 访问序——直接迭代 values() 不改变
             * LinkedHashMap(accessOrder=true) 的顺序，否则“常用”条目会被误淘汰。
             */
            return Optional.of(entries.get(best.question()));
        }
    }

    @Override
    public void put(Long userId, CacheEntry entry) {
        if (userId == null || entry == null || entry.expired(System.currentTimeMillis())) {
            return;
        }
        LinkedHashMap<String, CacheEntry> entries = userCaches.computeIfAbsent(
                userId, key -> newLinkedHashMap());
        synchronized (entries) {
            purgeExpired(entries, System.currentTimeMillis());
            entries.put(entry.question(), entry);
            // LRU 淘汰：超限时逐掉最久未用
            while (entries.size() > Math.max(1, properties.getMaxEntriesPerUser())) {
                Iterator<String> iterator = entries.keySet().iterator();
                if (!iterator.hasNext()) {
                    break;
                }
                iterator.next();
                iterator.remove();
            }
        }
    }

    // 访问序 LRU：get 也算一次访问
    private LinkedHashMap<String, CacheEntry> newLinkedHashMap() {
        return new LinkedHashMap<String, CacheEntry>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, CacheEntry> eldest) {
                return size() > Math.max(1, properties.getMaxEntriesPerUser());
            }
        };
    }

    private void purgeExpired(LinkedHashMap<String, CacheEntry> entries, long now) {
        entries.values().removeIf(entry -> entry.expired(now));
    }

    // 余弦相似度：dot / (|a| * |b|)；零向量返回 0（不可能命中）
    private double cosine(float[] left, float[] right) {
        if (left == null || right == null || left.length != right.length || left.length == 0) {
            return 0d;
        }
        double dot = 0d;
        double normLeft = 0d;
        double normRight = 0d;
        for (int i = 0; i < left.length; i++) {
            dot += (double) left[i] * right[i];
            normLeft += (double) left[i] * left[i];
            normRight += (double) right[i] * right[i];
        }
        if (normLeft <= 0d || normRight <= 0d) {
            return 0d;
        }
        return dot / (Math.sqrt(normLeft) * Math.sqrt(normRight));
    }
}
