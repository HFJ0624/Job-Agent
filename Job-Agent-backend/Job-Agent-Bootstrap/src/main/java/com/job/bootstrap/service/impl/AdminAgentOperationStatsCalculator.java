package com.job.bootstrap.service.impl;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Agent 运营看板统计计算器。
 *
 * 说明：
 * 1. 纯计算逻辑独立出来，避免 Service 里混杂太多百分比细节。
 * 2. 分母为 0 时统一返回 0，前端不需要处理 NaN。
 */
@Component
public class AdminAgentOperationStatsCalculator {

    /**
     * 计算百分比，保留 2 位小数。
     */
    public double rate(long numerator, long denominator) {
        if (denominator <= 0) {
            return 0.0;
        }
        return BigDecimal.valueOf(numerator)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(denominator), 2, RoundingMode.HALF_UP)
                .doubleValue();
    }
}
