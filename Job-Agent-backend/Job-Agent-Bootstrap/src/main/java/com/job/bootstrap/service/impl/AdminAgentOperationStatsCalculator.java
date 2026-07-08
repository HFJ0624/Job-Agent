package com.job.bootstrap.service.impl;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Agent 运营看板统计计算器。
 *
 * <p>核心职责：为运营看板提供统一的百分比计算能力，将纯计算逻辑从 Service 中剥离，
 * 避免运营指标统计细节污染业务编排层。</p>
 *
 * <p>所属业务模块：Agent 运营中心 - 统计工具</p>
 *
 * <p>主要调用链：
 * {@link AdminAgentOperationServiceImpl} → {@link AdminAgentOperationStatsCalculator#rate} → 返回百分比数值</p>
 *
 * <p>与其他核心组件的关系：
 * <ul>
 *   <li>被 {@link AdminAgentOperationServiceImpl} 依赖注入，用于计算日报成功率、行动项完成率等</li>
 * </ul></p>
 *
 * <p>设计说明：
 * <ul>
 *   <li>纯计算逻辑独立出来，避免 Service 里混杂太多百分比细节。</li>
 *   <li>分母为 0 时统一返回 0，前端不需要处理 NaN。</li>
 *   <li>使用 {@link BigDecimal} 进行除法运算，保留 2 位小数并采用 HALF_UP 舍入模式。</li>
 * </ul></p>
 */
@Component
public class AdminAgentOperationStatsCalculator {

    /**
     * 计算百分比，保留 2 位小数。
     *
     * <p>当分母小于等于 0 时直接返回 0.0，避免前端处理除零异常或 NaN。</p>
     *
     * @param numerator 分子，如成功次数
     * @param denominator 分母，如总次数
     * @return 百分比数值，保留 2 位小数
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
