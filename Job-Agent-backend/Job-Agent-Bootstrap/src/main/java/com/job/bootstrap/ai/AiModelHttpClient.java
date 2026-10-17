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

    /**
     * 以 SSE 流式方式调用 OpenAI 兼容模型接口（任务 G5）。
     *
     * 说明:
     * 1. 请求体带 stream=true，逐行读取 data 事件，把 choices[0].delta.content 增量回调给 consumer；
     * 2. 供应商不支持流式或网络失败时抛异常，由调用方（网关）决定降级策略；
     * 3. 实现不负责重试——流式中途重试会重复推送 token，重试决策在网关层。
     *
     * @param model         模型配置
     * @param systemPrompt  系统提示词
     * @param userMessage   用户消息
     * @param consumer      token 消费者
     */
    void chatStream(AiModelConfig model, String systemPrompt, String userMessage, TokenConsumer consumer);
}
