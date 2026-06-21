package com.job.bootstrap.service;

import java.util.Map;

/**
 * 作者:hfj
 * 功能:AI 模型统一调用网关
 * 日期:2026/6/21
 */
public interface AiModelGatewayService {

    /**
     * 按业务场景调用模型。
     *
     * @param sceneCode 业务场景编码，例如 AGENT_SUMMARY
     * @param variables Prompt 变量
     * @param userMessage 用户消息
     * @param userId 用户 ID
     * @param traceId 链路 ID
     * @return 模型输出文本
     */
    String chat(String sceneCode, Map<String, Object> variables, String userMessage, Long userId, String traceId);
}
