package com.job.bootstrap.ai;

import com.job.exception.BizException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AiRetryPolicy 单元测试（任务8）。
 *
 * 覆盖重点:
 * 1. 可重试判定：5xx / 429 / 超时IO 可重试；4xx（400/401）与业务异常不重试。
 * 2. 退避计算：指数增长并封顶；带抖动时落在 [base×2^(n-1) - jitter, base×2^(n-1)]。
 */
class AiRetryPolicyTest {

    private AiGatewayProperties properties;
    private AiRetryPolicy policy;

    @BeforeEach
    void setUp() {
        properties = new AiGatewayProperties();
        policy = new AiRetryPolicy(properties);
    }

    @Test
    void serverErrorIsRetryable() {
        assertThat(policy.isRetryable(new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR))).isTrue();
    }

    @Test
    void tooManyRequestsIsRetryable() {
        assertThat(policy.isRetryable(new HttpClientErrorException(HttpStatus.TOO_MANY_REQUESTS))).isTrue();
    }

    @Test
    void clientErrorIsNotRetryable() {
        assertThat(policy.isRetryable(new HttpClientErrorException(HttpStatus.BAD_REQUEST))).isFalse();
        assertThat(policy.isRetryable(new HttpClientErrorException(HttpStatus.UNAUTHORIZED))).isFalse();
    }

    @Test
    void timeoutIoIsRetryable() {
        assertThat(policy.isRetryable(new ResourceAccessException("read timed out"))).isTrue();
    }

    @Test
    void bizExceptionIsNotRetryable() {
        assertThat(policy.isRetryable(new BizException("模型 API Key 为空"))).isFalse();
    }

    @Test
    void backoffIsExponentialAndCapped() {
        properties.setRetryBaseDelayMs(100);
        properties.setRetryMaxDelayMs(1000);
        properties.setRetryJitterMs(0);
        assertThat(policy.backoffDelayMs(1)).isEqualTo(100);
        assertThat(policy.backoffDelayMs(2)).isEqualTo(200);
        assertThat(policy.backoffDelayMs(3)).isEqualTo(400);
        assertThat(policy.backoffDelayMs(10)).isEqualTo(1000); // 封顶
    }

    @Test
    void backoffWithJitterStaysWithinRange() {
        properties.setRetryBaseDelayMs(100);
        properties.setRetryMaxDelayMs(100000);
        properties.setRetryJitterMs(50);
        long delay = policy.backoffDelayMs(1); // 基数 100，抖动后 ∈ [50,100]
        assertThat(delay).isBetween(50L, 100L);
    }
}
