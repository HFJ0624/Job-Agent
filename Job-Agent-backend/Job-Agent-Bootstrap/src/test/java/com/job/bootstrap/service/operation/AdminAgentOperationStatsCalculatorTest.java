package com.job.bootstrap.service.operation;

import com.job.bootstrap.service.impl.AdminAgentOperationStatsCalculator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Agent 运营看板统计计算测试。
 */
class AdminAgentOperationStatsCalculatorTest {

    @Test
    void shouldCalculateRateSafely() {
        AdminAgentOperationStatsCalculator calculator = new AdminAgentOperationStatsCalculator();

        assertThat(calculator.rate(8, 10)).isEqualTo(80.0);
        assertThat(calculator.rate(0, 0)).isEqualTo(0.0);
    }
}
