package com.job.bootstrap.ai;

import com.job.common.entity.ai.AiModelRoute;
import com.job.common.entity.ai.AiPromptVersion;

/**
 * 作者:hfj
 * 功能:运行时已经选中并渲染完成的 Prompt
 * 日期:2026/6/21
 *
 * @param route 当前命中的模型路由
 * @param promptVersion 当前命中的 Prompt 版本
 * @param systemPrompt 已完成变量替换的系统提示词
 */
public record AiRenderedPrompt(
        AiModelRoute route,
        AiPromptVersion promptVersion,
        String systemPrompt
) {
}
