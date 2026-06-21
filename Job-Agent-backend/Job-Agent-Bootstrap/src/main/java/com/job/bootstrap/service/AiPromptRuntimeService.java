package com.job.bootstrap.service;

import com.job.bootstrap.ai.AiRenderedPrompt;
import com.job.common.entity.ai.AiModelRoute;

import java.util.Map;

/**
 * 作者:hfj
 * 功能:AI Prompt 运行时解析服务
 * 日期:2026/6/21
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
