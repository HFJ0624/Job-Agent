package com.job.bootstrap.ai.turn;

import java.util.Map;

/**
 * 功能: 暴露给模型的工具定义（第一阶段）。
 *
 * 说明: 只声明 Agent 白名单内的工具；参数结构用 JSON Schema 描述，
 * 模型据此生成 arguments，执行器再用同一套 Schema 做服务端校验（模型输出不可信）。
 *
 * @param name        工具名（对模型暴露的别名，如 job_search）
 * @param description 用途说明
 * @param parameters  参数 JSON Schema
 */
public record AiToolSpec(String name, String description, Map<String, Object> parameters) {
}
