package com.job.config;

import com.job.agent.InterviewInviteExtractorAssistant;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 作者: hfj
 * 功能: 面试邀约信息提取 Assistant 配置
 */
@Configuration
public class InterviewInviteExtractorConfig {

    /**
     * 构建 InterviewInviteExtractorAssistant。
     *
     * @param chatModel 项目已有大模型 Bean
     * @return 面试邀约信息提取助手
     */
    @Bean
    public InterviewInviteExtractorAssistant interviewInviteExtractorAssistant(ChatModel chatModel) {
        return AiServices.builder(InterviewInviteExtractorAssistant.class)
                .chatModel(chatModel)
                .build();
    }
}
