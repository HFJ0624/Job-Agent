package com.job.config;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import java.time.Duration;
/**
 * 作者:hfj
 * 功能:LangChain4j 基础配置
 * 说明:
 * 1. ChatModel 用于普通非流式对话。
 * 2. ChatMemoryProvider 根据 conversationId 管理多轮上下文。
 * 3. 第一版使用内存记忆，消息仍然会落库；后续可改成数据库记忆。
 * 日期: 2026/6/8 15:00
 */
@Configuration
public class LangChain4jConfig {

    @Bean
    public ChatModel chatLanguageModel(AiProperties properties) {
        if (!StringUtils.hasText(properties.getApiKey())) {
            throw new IllegalStateException("请配置 job.ai.api-key 或环境变量 JOB_AI_API_KEY");
        }

        return OpenAiChatModel.builder()
                .apiKey(properties.getApiKey())
                .baseUrl(properties.getBaseUrl())
                .modelName(properties.getModelName())
                .temperature(properties.getTemperature())
                /*
                 * Web 请求场景下不要让模型调用阻塞太久。
                 * 如果供应商网络抖动，业务层会做兜底；这里关闭 SDK 重试，避免一次点击等待多轮 60s 超时。
                 */
                .timeout(Duration.ofSeconds(20))
                .maxRetries(0)
                .build();
    }

    @Bean
    public ChatMemoryProvider chatMemoryProvider() {
        return memoryId -> {
            /*
             * 每个 conversationId 对应一个 MessageWindowChatMemory。
             * maxMessages 表示最多保留最近多少条上下文，避免上下文过长。
             */
            return MessageWindowChatMemory.withMaxMessages(12);
        };
    }
}
