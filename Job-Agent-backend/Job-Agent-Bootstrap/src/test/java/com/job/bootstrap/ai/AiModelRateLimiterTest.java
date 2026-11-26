package com.job.bootstrap.ai;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AiModelRateLimiter 单元测试（任务8）。
 *
 * 覆盖重点:
 * 1. 令牌桶按容量放行，令牌耗尽即限流（测试在微秒级完成，补充令牌可忽略）。
 * 2. 不同 key（场景/用户）各有独立令牌桶，互不影响。
 */
class AiModelRateLimiterTest {

    @Test
    void shouldAllowUpToCapacityThenReject() {
        AiGatewayProperties properties = new AiGatewayProperties();
        properties.setSceneQps(1.0);
        AiModelRateLimiter limiter = new AiModelRateLimiter(properties);

        assertThat(limiter.tryAcquireScene("AGENT_SUMMARY")).isTrue();
        assertThat(limiter.tryAcquireScene("AGENT_SUMMARY")).isFalse();
    }

    @Test
    void differentScenesHaveSeparateBuckets() {
        AiGatewayProperties properties = new AiGatewayProperties();
        properties.setSceneQps(1.0);
        AiModelRateLimiter limiter = new AiModelRateLimiter(properties);

        assertThat(limiter.tryAcquireScene("A")).isTrue();
        assertThat(limiter.tryAcquireScene("B")).isTrue();
        assertThat(limiter.tryAcquireScene("A")).isFalse();
    }

    @Test
    void userBucketSeparateFromSceneBucket() {
        AiGatewayProperties properties = new AiGatewayProperties();
        properties.setSceneQps(5.0);
        properties.setUserQps(1.0);
        AiModelRateLimiter limiter = new AiModelRateLimiter(properties);

        assertThat(limiter.tryAcquireScene("AGENT_SUMMARY")).isTrue();
        assertThat(limiter.tryAcquireUser(100L)).isTrue();
        assertThat(limiter.tryAcquireUser(100L)).isFalse();
    }
}
