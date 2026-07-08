package com.job.config;

import com.job.agent.InterviewInviteExtractorAssistant;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 面试邀约信息提取 Assistant 配置。
 *
 * <p>核心职责：
 * 复用项目已有 ChatModel，构建 InterviewInviteExtractorAssistant 代理实例，
 * 用于从 HR 回复文本中抽取结构化面试邀约信息。</p>
 *
 * <p>所属业务模块：Job-Agent-Infra-Ai 模块下的配置层。</p>
 *
 * <p>主要调用链：
 * InterviewInviteExtractorConfig -> AiServices.builder -> InterviewInviteExtractorAssistant
 * -> HrReplyRecognitionService.recognize 调用 extract 抽取面试信息</p>
 *
 * <p>与其他核心组件的关系：
 * <ul>
 *   <li>仅当容器中存在 ChatModel Bean 时才生效；</li>
 *   <li>共享 LangChain4jConfig 注册的 ChatModel；</li>
 *   <li>输出 JSON 由 HrReplyRecognitionService 用 Jackson 解析并落库。</li>
 * </ul></p>
 *
 * 作者: hfj
 */
@Configuration
@ConditionalOnBean(ChatModel.class)
public class InterviewInviteExtractorConfig {

    /**
     * 构建面试邀约信息提取助手代理实例。
     *
     * @param chatModel 项目已有大模型 Bean
     * @return 面试邀约信息提取助手代理对象，由 LangChain4j 框架生成
     */
    @Bean
    public InterviewInviteExtractorAssistant interviewInviteExtractorAssistant(ChatModel chatModel) {
        return AiServices.builder(InterviewInviteExtractorAssistant.class)
                .chatModel(chatModel)
                .build();
    }
}
