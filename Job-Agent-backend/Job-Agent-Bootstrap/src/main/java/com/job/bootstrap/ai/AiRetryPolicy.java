package com.job.bootstrap.ai;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 功能: 模型网关重试策略（任务8）。
 *
 * 设计说明:
 * 1. 只对“可能瞬秒恢复”的错误重试：5xx、429（限流）、超时/IO（ResourceAccessException）。
 * 2. 4xx 业务错误（如 401 无权限、400 参数错）重试无意义，直接不重试，交由外层切备用模型。
 * 3. 退避用指数增长 + 抖动并封顶，避免并发请求同步重试把下游打得更惨。
 */
@Component
@RequiredArgsConstructor
public class AiRetryPolicy {

    private final AiGatewayProperties properties;

    /**
     * 判断异常是否值得重试。
     */
    public boolean isRetryable(Exception exception) {
        if (exception instanceof HttpServerErrorException) {
            return true;
        }
        if (exception instanceof HttpClientErrorException clientError) {
            // 429 Too Many Requests 可重试，其余 4xx 不重试
            return clientError.getStatusCode().value() == 429;
        }
        // 连接超时、读超时、IO 异常等
        return exception instanceof ResourceAccessException;
    }

    /**
     * 计算第 attempt 次重试前的退避时间（毫秒）：base × 2^(attempt-1)，封顶 max，再减 [0, jitter] 抖动。
     */
    public long backoffDelayMs(int attempt) {
        long base = Math.max(0, properties.getRetryBaseDelayMs());
        long max = Math.max(base, properties.getRetryMaxDelayMs());
        long jitter = Math.max(0, properties.getRetryJitterMs());

        long exponential = (long) (base * Math.pow(2, Math.max(0, attempt - 1)));
        long capped = Math.min(exponential, max);
        long jitterOffset = jitter == 0 ? 0 : ThreadLocalRandom.current().nextLong(jitter + 1);
        return Math.max(0, capped - jitterOffset);
    }
}
