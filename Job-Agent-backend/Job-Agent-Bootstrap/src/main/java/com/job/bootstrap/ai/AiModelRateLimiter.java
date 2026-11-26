package com.job.bootstrap.ai;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 功能: 模型网关限流器（任务8）。
 *
 * 设计说明:
 * 1. 按 sceneCode 与 userId 两个维度各用一个令牌桶限流，防止某场景或某用户突发流量打爆模型供应商。
 * 2. 手写令牌桶（零新增依赖）：按速率补充许可，tryAcquire 不阻塞，拿不到许可即快速失败，
 *    由调用方走降级，避免请求堆积。
 * 3. 速率为 0 或负数的 key 会被兜底成一个极小值，保证限流器始终可用。
 */
@Component
@RequiredArgsConstructor
public class AiModelRateLimiter {

    private final AiGatewayProperties properties;

    private final ConcurrentHashMap<String, TokenBucket> buckets = new ConcurrentHashMap<>();

    /**
     * 尝试获取场景维度的 1 个许可。
     *
     * @return true 放行；false 触发限流
     */
    public boolean tryAcquireScene(String sceneCode) {
        String key = "scene:" + (sceneCode == null ? "unknown" : sceneCode);
        return bucket(key, properties.getSceneQps()).tryAcquire();
    }

    /**
     * 尝试获取用户维度的 1 个许可。
     *
     * @return true 放行；false 触发限流
     */
    public boolean tryAcquireUser(Long userId) {
        String key = "user:" + (userId == null ? "anon" : userId);
        return bucket(key, properties.getUserQps()).tryAcquire();
    }

    private TokenBucket bucket(String key, double qps) {
        return buckets.computeIfAbsent(key, k -> new TokenBucket(qps));
    }

    /**
     * 极简令牌桶：按时间补充许可，容量=速率，tryAcquire 无阻塞。
     */
    static final class TokenBucket {

        private final double permitsPerSecond;
        private final double capacity;
        private double available;
        private long lastRefillNanos;

        TokenBucket(double qps) {
            this.permitsPerSecond = Math.max(0.0001, qps);
            this.capacity = Math.max(1.0, qps);
            this.available = capacity;
            this.lastRefillNanos = System.nanoTime();
        }

        synchronized boolean tryAcquire() {
            refill();
            if (available >= 1.0) {
                available -= 1.0;
                return true;
            }
            return false;
        }

        private void refill() {
            long now = System.nanoTime();
            double elapsedSeconds = (now - lastRefillNanos) / 1_000_000_000.0;
            if (elapsedSeconds > 0) {
                available = Math.min(capacity, available + elapsedSeconds * permitsPerSecond);
                lastRefillNanos = now;
            }
        }
    }
}
