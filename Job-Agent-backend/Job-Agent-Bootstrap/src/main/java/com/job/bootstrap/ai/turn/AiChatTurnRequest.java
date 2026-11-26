package com.job.bootstrap.ai.turn;

import java.util.List;

/**
 * 功能: 结构化模型轮次请求（第一阶段）。
 *
 * 说明:
 * 1. 与旧 chat() 的差异：完整消息序列 + 工具清单 + tool_choice，支撑 ReAct 循环；
 * 2. messages 由 AgentRunContext 持有（每次 run 独立，禁止全局共享可变历史）；
 * 3. tools 为空时退化为普通对话（服务端仍走同一网关流水线）。
 *
 * @param messages   完整消息序列
 * @param tools      可用工具（白名单）
 * @param toolChoice auto / none / required（由 AgentDefinition 预算与协议决定）
 */
public record AiChatTurnRequest(
        List<AiChatMessage> messages,
        List<AiToolSpec> tools,
        String toolChoice
) {

    public static final String TOOL_CHOICE_AUTO = "auto";

    public static AiChatTurnRequest of(List<AiChatMessage> messages, List<AiToolSpec> tools) {
        return new AiChatTurnRequest(messages, tools, TOOL_CHOICE_AUTO);
    }
}
