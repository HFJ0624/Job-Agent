package com.job.config;

import com.job.agent.HrCommunicationAssistant;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * HR 沟通回复 AI 配置。
 *
 * <p>核心职责：
 * 复用项目已有 ChatModel，单独构建 HrCommunicationAssistant 代理实例，
 * 避免把 HR 回复生成逻辑塞进 JobAgentAssistant，保持职责单一。</p>
 *
 * <p>所属业务模块：Job-Agent-Infra-Ai 模块下的配置层。</p>
 *
 * <p>主要调用链：
 * HrCommunicationAssistantConfig -> AiServices.builder -> HrCommunicationAssistant
 * -> JobCommunicationRecordService 调用 generateReply 生成回复正文</p>
 *
 * <p>与其他核心组件的关系：
 * <ul>
 *   <li>仅当容器中存在 ChatModel Bean 时才生效，保证 legacy-enabled=false 时不创建多余 Bean；</li>
 *   <li>共享 LangChain4jConfig 注册的 ChatModel，不单独配置 API Key。</li>
 * </ul></p>
 *
 * 作者: hfj
 */
@Configuration
@ConditionalOnBean(ChatModel.class)
public class HrCommunicationAssistantConfig {

    /**
     * 构建 HR 沟通回复助手代理实例。
     *
     * @param chatModel 项目已有的大模型 Bean
     * @return HrCommunicationAssistant 代理对象，由 LangChain4j 框架生成
     */
    @Bean
    public HrCommunicationAssistant hrCommunicationAssistant(ChatModel chatModel) {
        return AiServices.builder(HrCommunicationAssistant.class)
                .chatModel(chatModel)
                .build();
    }
}
