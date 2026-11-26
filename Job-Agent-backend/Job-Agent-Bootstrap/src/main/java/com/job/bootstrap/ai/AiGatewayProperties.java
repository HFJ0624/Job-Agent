package com.job.bootstrap.ai;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 功能: 模型网关稳定性配置（任务8）。
 *
 * 说明:
 * 1. 限流用 QPS（每秒许可数），退避用「基数 × 2^(attempt-1) ± 抖动」并封顶。
 * 2. 默认值偏保守，适合开发/演示；生产可在 application-*.yml 按场景调整。
 */
@Data
@ConfigurationProperties(prefix = "job.ai.gateway")
public class AiGatewayProperties {

    /**
     * 单场景每秒请求上限（令牌桶速率）。
     */
    private double sceneQps = 5.0;

    /**
     * 单用户每秒请求上限（令牌桶速率），防止单个用户把场景配额占满。
     */
    private double userQps = 2.0;

    /**
     * 退避基数（毫秒）：第 1 次重试约等 base，之后翻倍。
     */
    private long retryBaseDelayMs = 500;

    /**
     * 退避上限（毫秒），防止指数增长过大。
     */
    private long retryMaxDelayMs = 4000;

    /**
     * 退避抖动（毫秒），在计算值上随机减去 [0, jitter]，避免并发请求同步重试。
     */
    private long retryJitterMs = 200;
}
