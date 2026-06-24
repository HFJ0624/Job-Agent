package com.job.config;

import com.job.agent.HrCommunicationAssistant;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 作者: hfj
 * 功能: HR 沟通回复 AI 配置
 * 说明:
 * 1. 使用项目已有 ChatModel。
 * 2. 单独构建 HrCommunicationAssistant。
 * 3. 避免把 HR 回复生成逻辑塞进 JobAgentAssistant。
 */
@Configuration
@ConditionalOnBean(ChatModel.class)
public class HrCommunicationAssistantConfig {

    @Bean
    public HrCommunicationAssistant hrCommunicationAssistant(ChatModel chatModel) {
        return AiServices.builder(HrCommunicationAssistant.class)
                .chatModel(chatModel)
                .build();
    }
}
