package com.job.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
/**
 * AI 模型配置属性。
 *
 * <p>核心职责：
 * 绑定 application.yml 中 job.ai.* 配置项，为历史 LangChain4j 链路提供 API Key、模型地址、
 * 模型名称、温度、超时等参数。</p>
 *
 * <p>所属业务模块：Job-Agent-Infra-Ai 模块下的配置层。</p>
 *
 * <p>主要调用链：
 * application.yml -> AiProperties -> LangChain4jConfig -> ChatModel -> AI Service</p>
 *
 * <p>与其他核心组件的关系：
 * <ul>
 *   <li>被 LangChain4jConfig 注入用于构建 ChatModel；</li>
 *   <li>legacyEnabled=false 时本配置仅作兼容保留，模型调用统一走 AiModelGatewayService 数据库网关；</li>
 *   <li>resumeScoreTimeoutSeconds / resumeScoreMaxTokens 专用于简历评分，避免长简历超时。</li>
 * </ul></p>
 *
 * 作者:hfj
 * 日期: 2026/6/8 14:59
 */
@Data
@Component
@ConfigurationProperties(prefix = "job.ai")
public class AiProperties {

    /**
     * 大模型 API Key。
     */
    private String apiKey;

    /**
     * OpenAI 兼容接口地址。
     */
    private String baseUrl;

    /**
     * 模型名称。
     */
    private String modelName;

    /**
     * 温度参数，越低越稳定。
     */
    private Double temperature = 0.3;

    /**
     * 是否启用旧 LangChain4j Assistant。
     *
     * 说明:
     * 1. false 时，系统不再从 application.yml 的 job.ai 创建旧 ChatModel。
     * 2. 模型调用统一走数据库模型网关 ai_model_config / ai_model_route。
     * 3. 如果后续确实要临时回退旧 Assistant，可以在本地配置 legacy-enabled: true。
     */
    private Boolean legacyEnabled = false;

    /**
     * 简历评分模型超时时间。
     * 说明: 简历评分需要读取较长文本并返回结构化 JSON，比普通聊天更慢，所以单独配置超时。
     */
    private Integer resumeScoreTimeoutSeconds = 180;

    /**
     * 简历评分模型最大输出 token 数。
     * 说明: 限制输出长度可以减少火山方舟长时间生成导致的 Request cancelled。
     */
    private Integer resumeScoreMaxTokens = 1800;
}
