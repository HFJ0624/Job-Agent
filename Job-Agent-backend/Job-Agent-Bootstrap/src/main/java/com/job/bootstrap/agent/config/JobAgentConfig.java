package com.job.bootstrap.agent.config;

import com.job.agent.JobAgentAssistant;
import com.job.bootstrap.agent.tools.*;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 作者:hfj
 * 功能:Job-Agent 助手配置
 * 设计说明:
 * 1. JobAgentAssistant 是 LangChain4j AI Service 接口。
 * 2. 这里通过 AiServices.builder 创建真正的 Spring Bean。
 * 3. tools(...) 里注册的 Java 对象，就是大模型可以调用的业务工具。
 * 4. 这一步做完后，Agent 才真正具备“调用后端业务能力”的能力。
 * 日期: 2026/6/8 15:19
 */
@Configuration
@RequiredArgsConstructor
public class JobAgentConfig {

    private final ResumeAnalyzeTool resumeAnalyzeTool;
    private final JobMatchTool jobMatchTool;
    private final GreetingGenerateTool greetingGenerateTool;
    private final JobSearchTool jobSearchTool;
    private final JobRecommendTool jobRecommendTool;
    private final InterviewPrepareTool interviewPrepareTool;
    private final MockInterviewReviewTool mockInterviewReviewTool;
    private final RagSearchTool ragSearchTool;

    /**
     * 构建 JobAgentAssistant。
     *
     * @param chatModel 大模型对象，来自 LangChain4jConfig
     * @param chatMemoryProvider 多轮记忆对象，来自 LangChain4jConfig
     * @return JobAgentAssistant 代理对象
     */
    @Bean
    public JobAgentAssistant jobAgentAssistant(
            ChatModel chatModel,
            ChatMemoryProvider chatMemoryProvider
    ) {
        return AiServices.builder(JobAgentAssistant.class)
                /*
                 * 配置大模型。
                 */
                .chatModel(chatModel)

                /*
                 * 配置多轮记忆。
                 * conversationId 会作为 memoryId 传入。
                 */
                .chatMemoryProvider(chatMemoryProvider)

                /*
                 * 注册工具。
                 * 大模型根据用户输入和 @Tool 描述决定是否调用这些工具。
                 */
                .tools(
                        jobMatchTool,
                        jobSearchTool,
                        greetingGenerateTool,
                        interviewPrepareTool,
                        resumeAnalyzeTool,
                        jobRecommendTool,
                        mockInterviewReviewTool,
                        ragSearchTool
                )
                .build();
    }
}
