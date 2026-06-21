package com.job.bootstrap.ai;

/**
 * 作者:hfj
 * 功能:模型网关内部调用结果
 * 日期:2026/6/21
 *
 * @param content 模型输出文本
 * @param inputTokens 输入 token 数
 * @param outputTokens 输出 token 数
 */
public record AiModelCallResponse(
        String content,
        Integer inputTokens,
        Integer outputTokens
) {
}
