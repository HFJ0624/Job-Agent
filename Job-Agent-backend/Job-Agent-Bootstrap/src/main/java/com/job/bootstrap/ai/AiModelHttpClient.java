package com.job.bootstrap.ai;

import com.job.common.entity.ai.AiModelConfig;

/**
 * 功能: 模型 HTTP 客户端接口（任务8）。
 *
 * 设计说明:
 * 1. 把“调用 OpenAI 兼容接口并解析响应”从网关编排里抽出来，便于单测用 mock 替换，
 *    从而在不调用真实收费 API 的前提下验证重试、熔断、限流。
 * 2. 实现默认走 RestTemplate（见 RestTemplateAiModelHttpClient）。
 */
public interface AiModelHttpClient {

    /**
     * 调用一次 OpenAI 兼容模型接口。
     *
     * @param model         模型配置
     * @param systemPrompt  系统提示词
     * @param userMessage   用户消息
     * @return 模型输出与 token 用量
     */
    AiModelCallResponse chat(AiModelConfig model, String systemPrompt, String userMessage);
}
