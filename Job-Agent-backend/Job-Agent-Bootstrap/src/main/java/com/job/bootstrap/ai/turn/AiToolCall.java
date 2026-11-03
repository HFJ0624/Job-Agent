package com.job.bootstrap.ai.turn;

/**
 * 功能: 模型返回的工具调用（第一阶段）。
 *
 * 说明:
 * 1. argumentsJson 是模型生成的原始 JSON 字符串——**必须由执行器校验后使用**，
 *    不能直接当可信参数（非法 JSON、缺参数、越权字段都要在执行器拦截）；
 * 2. id 用于与 tool 消息配对（OpenAI 兼容协议 tool_call_id）。
 *
 * @param id            工具调用 ID（与 tool 消息配对）
 * @param name          工具名（Agent 别名或 Java 工具名，由 Runner 映射）
 * @param argumentsJson 参数 JSON 原文
 */
public record AiToolCall(String id, String name, String argumentsJson) {
}
