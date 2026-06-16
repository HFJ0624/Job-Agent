package com.job.config;

import com.job.agent.ResumeScoreAssistant;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 作者:hfj
 * 功能:AI 简历评分 V2 Assistant 配置
 * 日期:2026/6/15
 *
 * 设计说明:
 * 1. 复用项目已有的 API Key 和模型配置。
 * 2. ResumeScoreAssistant 负责生成 AI 维度分和结构化点评，最终落库和分数校验由业务 Service 控制。
 * 3. 使用 resumeScoreChatModel 专用 Bean，给简历评分更长超时，避免 Request cancelled。
 */
@Configuration
public class ResumeScoreAssistantConfig {

    /**
     * 构建 AI 简历评分辅助分析 Assistant。
     *
     * @param chatModel 简历评分专用大模型 ChatModel
     * @return ResumeScoreAssistant 代理对象
     */
    @Bean
    public ResumeScoreAssistant resumeScoreAssistant(@Qualifier("resumeScoreChatModel") ChatModel chatModel) {
        return AiServices.builder(ResumeScoreAssistant.class)
                .chatModel(chatModel)
                .build();
    }
}
