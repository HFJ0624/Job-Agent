package com.job.bootstrap.agent.config;


import com.job.agent.JobAgentAssistant;
import com.job.bootstrap.agent.tools.GreetingGenerateTool;
import com.job.bootstrap.agent.tools.JobMatchTool;
import com.job.bootstrap.agent.tools.JobSearchTool;
import com.job.bootstrap.agent.tools.ResumeAnalyzeTool;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 作者:hfj
 * 功能:Job-Agent 助手配置
 * 说明:
 * 1. 这里把 ChatModel、Memory 和 Tools 绑定到 JobAgentAssistant。
 * 2. 绑定后，模型就可以根据工具描述自动选择工具调用。
 * 日期: 2026/6/8 15:19
 */
@Configuration
public class JobAgentConfig {

    /**
     * 创建 JobAgentAssistant Bean。
     *
     * @param chatModel LangChain4j 对话模型
     * @param chatMemoryProvider 多轮对话记忆提供器
     * @param resumeAnalyzeTool 简历分析工具
     * @param jobMatchTool 岗位匹配工具
     * @param greetingGenerateTool HR 打招呼语生成工具
     * @param jobSearchTool 岗位搜索工具
     * @return Job-Agent AI 助手
     */
    @Bean
    public JobAgentAssistant jobAgentAssistant(
            ChatModel chatModel,
            ChatMemoryProvider chatMemoryProvider,
            ResumeAnalyzeTool resumeAnalyzeTool,
            JobMatchTool jobMatchTool,
            GreetingGenerateTool greetingGenerateTool,
            JobSearchTool jobSearchTool
    ) {
        return AiServices.builder(JobAgentAssistant.class)
                .chatModel(chatModel)
                .chatMemoryProvider(chatMemoryProvider)
                .tools(
                        resumeAnalyzeTool,
                        jobMatchTool,
                        greetingGenerateTool,
                        jobSearchTool
                )
                .build();
    }
}
