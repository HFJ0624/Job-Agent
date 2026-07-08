package com.job.bootstrap.service;

import com.job.bootstrap.ai.AiRenderedPrompt;
import com.job.common.entity.ai.AiModelRoute;

import java.util.Map;

/**
 * AI Prompt 运行时解析服务接口。
 *
 * <p>核心职责：根据已命中的模型路由，加载对应 Prompt 模板版本，完成变量替换和最终文本渲染，为模型调用提供标准化输入。</p>
 *
 * <p>所属业务模块：基础设施 - AI Prompt 引擎</p>
 *
 * <p>主要调用链：
 * AiModelGatewayService -&gt; AiPromptRuntimeService -&gt; AiPromptRuntimeServiceImpl -&gt; AiPromptTemplateRepository / TemplateEngine</p>
 */
public interface AiPromptRuntimeService {

    /**
     * 根据模型路由解析 Prompt 版本，并完成变量渲染。
     *
     * @param route 已命中的模型路由
     * @param variables Prompt 变量
     * @return 已渲染 Prompt
     */
    AiRenderedPrompt renderPrompt(AiModelRoute route, Map<String, Object> variables);
}
