package com.job.config;

import com.job.agent.ResumeScoreAssistant;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 作者:hfj
 * 功能:AI 简历评分 V2 Assistant 配置
 * 日期:2026/6/15
 *
 * 设计说明:
 * 1. 复用项目已有的 ChatModel，不单独写 API Key。
 * 2. ResumeScoreAssistant 只做结构化点评生成，最终落库和分数校验由业务 Service 控制。
 * 3. 单独注册 Bean，后续如果要替换模型或接入专门的评分 Agent，不影响其他 AI 能力。
 */
@Configuration
public class ResumeScoreAssistantConfig {

    /**
     * 构建 AI 简历评分辅助分析 Assistant。
     *
     * @param chatModel 项目统一的大模型 ChatModel
     * @return ResumeScoreAssistant 代理对象
     */
    @Bean
    public ResumeScoreAssistant resumeScoreAssistant(ChatModel chatModel) {
        return AiServices.builder(ResumeScoreAssistant.class)
                .chatModel(chatModel)
                .build();
    }
}
