package com.job.bootstrap.agent.cache;

/**
 * 功能: 语义缓存存储契约（任务 G10）。
 *
 * 设计说明:
 * 1. 只定义“按用户 + 查询向量查找/写入答案”的最小接口，不绑定存储介质——
 *    v1 是进程内实现（InMemorySemanticCacheStore），后续要持久化/跨实例
 *    （pgvector 表）只需新增一个实现类，编排服务零改动；
 * 2. 存储必须自己处理过期与容量淘汰，调用方不感知；
 * 3. 用户隔离由实现保证：lookup 只可能在调用方传入的 userId 范围内命中。
 */
public interface CacheStore {

    /**
     * 查询语义相似的缓存答案。
     *
     * @param userId         用户 ID（隔离边界）
     * @param queryEmbedding 当前查询的向量
     * @param threshold      命中阈值（余弦相似度）
     * @return 命中条目；未命中返回 empty
     */
    java.util.Optional<CacheEntry> lookup(Long userId, float[] queryEmbedding, double threshold);

    /**
     * 写入缓存答案。
     *
     * @param userId 用户 ID
     * @param entry  缓存条目（含问题、向量、答案、过期时间）
     */
    void put(Long userId, CacheEntry entry);

    /**
     * 一条缓存条目。
     *
     * @param question  原始问题文本（自包含查询，用于后台排查与精确去重）
     * @param embedding 问题向量
     * @param answer    缓存的答案
     * @param expireAt  过期时间戳（毫秒）
     */
    record CacheEntry(String question, float[] embedding, String answer, long expireAt) {

        public boolean expired(long now) {
            return expireAt <= now;
        }
    }
}
