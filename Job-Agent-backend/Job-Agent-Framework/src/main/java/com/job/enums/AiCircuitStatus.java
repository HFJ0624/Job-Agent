package com.job.enums;

/**
 * 作者:hfj
 * 功能:模型熔断状态
 * 日期:2026/6/21
 */
public enum AiCircuitStatus {

    /**
     * 关闭熔断，正常调用。
     */
    CLOSED,

    /**
     * 熔断打开，暂时不调用该模型。
     */
    OPEN,

    /**
     * 半开状态：熔断冷却后放行单个探测请求，成功则转 CLOSED、失败则转 OPEN。
     * 已在模型网关（AiModelGatewayServiceImpl.isCircuitOpen）实现。
     */
    HALF_OPEN
}
