package com.job.config;

import com.job.agent.ResumeScoreAssistant;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * AI 简历评分 V2 Assistant 配置。
 *
 * <p>核心职责：
 * 注入简历评分专用 resumeScoreChatModel，构建 ResumeScoreAssistant 代理实例，
 * 让简历评分场景拥有更长超时和更稳定的低温度输出。</p>
 *
 * <p>所属业务模块：Job-Agent-Infra-Ai 模块下的配置层。</p>
 *
 * <p>主要调用链：
 * ResumeScoreAssistantConfig -> AiServices.builder -> ResumeScoreAssistant
 * -> JobResumeScoreService.score 调用 analyze 生成结构化评分</p>
 *
 * <p>与其他核心组件的关系：
 * <ul>
 *   <li>仅当容器中存在 resumeScoreChatModel Bean 时才生效；</li>
 *   <li>复用项目已有的 API Key 和模型配置，不单独维护；</li>
 *   <li>ResumeScoreAssistant 负责生成 AI 维度分和结构化点评，最终落库和分数校验由业务 Service 控制。</li>
 * </ul></p>
 *
 * <p>设计说明:
 * 1. 复用项目已有的 API Key 和模型配置。
 * 2. ResumeScoreAssistant 负责生成 AI 维度分和结构化点评，最终落库和分数校验由业务 Service 控制。
 * 3. 使用 resumeScoreChatModel 专用 Bean，给简历评分更长超时，避免 Request cancelled。</p>
 *
 * 作者:hfj
 * 日期:2026/6/15
 */
@Configuration
@ConditionalOnBean(name = "resumeScoreChatModel")
public class ResumeScoreAssistantConfig {

    /**
     * 构建 AI 简历评分辅助分析 Assistant。
     *
     * @param chatModel 简历评分专用大模型 ChatModel，由 LangChain4jConfig.resumeScoreChatModel 创建
     * @return ResumeScoreAssistant 代理对象，由 LangChain4j 框架生成
     */
    @Bean
    public ResumeScoreAssistant resumeScoreAssistant(@Qualifier("resumeScoreChatModel") ChatModel chatModel) {
        return AiServices.builder(ResumeScoreAssistant.class)
                .chatModel(chatModel)
                .build();
    }
}
