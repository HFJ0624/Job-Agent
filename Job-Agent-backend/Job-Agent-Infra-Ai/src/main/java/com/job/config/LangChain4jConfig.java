package com.job.config;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
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
    @Primary
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
                 * 模型调用超时设置。
                 * 说明:
                 * 1. 这是普通聊天、Agent 工具调用共用模型，不适合设置得过长。
                 * 2. 简历评分会使用下面单独的 resumeScoreChatModel。
                 * 3. 这里关闭 SDK 自动重试，避免一次用户请求被底层重试拖太久。
                 */
                .timeout(Duration.ofSeconds(45))
                .maxRetries(0)
                .build();
    }

    /**
     * 简历评分专用模型。
     * 说明:
     * 1. 用户截图里的 Request cancelled 本质是模型还没返回，HTTP 客户端先取消了请求。
     * 2. 简历评分输入比普通聊天长，还要求模型输出 JSON，因此需要更长的 timeout。
     * 3. maxTokens 限制输出长度，避免模型写太多导致生成时间继续拉长。
     * 4. 这个 Bean 只给 ResumeScoreAssistant 使用，不影响普通 AI 助手。
     */
    @Bean("resumeScoreChatModel")
    public ChatModel resumeScoreChatModel(AiProperties properties) {
        if (!StringUtils.hasText(properties.getApiKey())) {
            throw new IllegalStateException("请配置 job.ai.api-key 或环境变量 JOB_AI_API_KEY");
        }

        int timeoutSeconds = properties.getResumeScoreTimeoutSeconds() == null
                ? 180
                : Math.max(60, properties.getResumeScoreTimeoutSeconds());
        int maxTokens = properties.getResumeScoreMaxTokens() == null
                ? 1800
                : Math.max(800, properties.getResumeScoreMaxTokens());

        return OpenAiChatModel.builder()
                .apiKey(properties.getApiKey())
                .baseUrl(properties.getBaseUrl())
                .modelName(properties.getModelName())
                .temperature(0.1)
                .maxTokens(maxTokens)
                .timeout(Duration.ofSeconds(timeoutSeconds))
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
