package com.job.config;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.util.StringUtils;

import java.time.Duration;
/**
 * LangChain4j 基础配置。
 *
 * <p>核心职责：
 * 装配历史 LangChain4j 链路所需的 ChatModel、专用评分模型与 ChatMemoryProvider，
 * 为 JobAgentAssistant、ResumeScoreAssistant 等 AI Service 提供底层模型能力。</p>
 *
 * <p>所属业务模块：Job-Agent-Infra-Ai 模块下的配置层。</p>
 *
 * <p>主要调用链：
 * LangChain4jConfig -> ChatModel -> AiServices.builder -> JobAgentAssistant / ResumeScoreAssistant
 * ChatMemoryProvider 按 conversationId 维护多轮上下文。</p>
 *
 * <p>与其他核心组件的关系：
 * <ul>
 *   <li>通过 AiProperties 读取 job.ai.* 配置；</li>
 *   <li>仅当 job.ai.legacy-enabled=true 时启用，新链路统一走 AiModelGatewayService 数据库网关；</li>
 *   <li>ChatMemoryProvider 第一版使用内存记忆，消息仍然会落库；后续可改成数据库记忆。</li>
 * </ul></p>
 *
 * <p>设计说明：
 * 1. ChatModel 用于普通非流式对话。
 * 2. ChatMemoryProvider 根据 conversationId 管理多轮上下文。
 * 3. 第一版使用内存记忆，消息仍然会落库；后续可改成数据库记忆。</p>
 *
 * 作者:hfj
 * 日期: 2026/6/8 15:00
 */
@Configuration
@ConditionalOnProperty(prefix = "job.ai", name = "legacy-enabled", havingValue = "true")
public class LangChain4jConfig {

    /**
     * 创建历史链路共享的 ChatModel。
     *
     * <p>核心处理流程：
     * 1. 校验 API Key 已配置，缺失时抛 IllegalStateException 提示运维；
     * 2. 使用 OpenAiChatModel 构建兼容 OpenAI 协议的模型实例；
     * 3. 关闭 SDK 自动重试，避免一次用户请求被底层重试拖太久。</p>
     *
     * @param properties AI 模型配置属性
     * @return 普通聊天、Agent 工具调用共用的 ChatModel 实例
     */
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
     * 创建简历评分专用模型。
     *
     * <p>核心处理流程：
     * 1. 校验 API Key 已配置；
     * 2. 根据配置解析超时时间和最大输出 token，并保证下限，避免长简历导致 Request cancelled；
     * 3. 使用更低的 temperature(0.1) 保证评分稳定；
     * 4. 返回的 Bean 只给 ResumeScoreAssistant 使用，不影响普通 AI 助手。</p>
     *
     * <p>设计说明:
     * 1. 用户截图里的 Request cancelled 本质是模型还没返回，HTTP 客户端先取消了请求。
     * 2. 简历评分输入比普通聊天长，还要求模型输出 JSON，因此需要更长的 timeout。
     * 3. maxTokens 限制输出长度，避免模型写太多导致生成时间继续拉长。</p>
     *
     * @param properties AI 模型配置属性
     * @return 简历评分专用 ChatModel，超时与 token 上限更宽松
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

    /**
     * 创建多轮对话 Memory Provider。
     *
     * <p>核心处理流程：
     * 1. 按 memoryId（即 conversationId）懒加载 MessageWindowChatMemory；
     * 2. 每个 conversationId 对应独立的 Memory 实例，避免跨会话串扰；
     * 3. maxMessages 限制最近保留的消息条数，控制上下文 token 成本。</p>
     *
     * @return LangChain4j ChatMemoryProvider
     */
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
