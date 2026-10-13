package com.job.bootstrap.agent.executor;

import lombok.Builder;
import lombok.Data;

/**
 * 作者:hfj
 * 功能:Agent 工具执行结果
 * 日期:2026/6/20
 */
@Data
@Builder
public class AgentToolExecutionResult {

    /**
     * 工具是否执行成功。
     */
    private Boolean success;

    /**
     * 工具名称。
     */
    private String toolName;

    /**
     * 统一错误码。
     */
    private String errorCode;

    /**
     * 执行提示或错误信息。
     */
    private String message;

    /**
     * 工具原始返回 JSON。
     * 第一版保留字符串，避免对每个 VO 做额外适配。
     */
    private String dataJson;

    /**
     * 是否为幂等命中。
     *
     * true 表示本次没有真实调用工具，而是直接采用了历史执行结果，
     * 用于步骤级幂等和失败恢复场景，方便 Trace 和后台区分真实执行与结果复用。
     */
    private Boolean idempotentHit;

    /**
     * 耗时，单位毫秒。
     */
    private Long costTime;
}
