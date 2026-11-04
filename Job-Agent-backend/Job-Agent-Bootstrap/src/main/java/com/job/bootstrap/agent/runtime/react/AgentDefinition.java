package com.job.bootstrap.agent.runtime.react;

import com.job.bootstrap.ai.turn.AiToolSpec;

import java.util.List;
import java.util.Map;

/**
 * 功能: Agent 定义（第一阶段 ReAct 内核）。
 *
 * 设计说明:
 * 1. 一个 Agent = 角色 Prompt + 工具白名单 + 输出契约 + 预算；ReAct 只负责“在预算内选工具”，
 *    身份/权限/条件校验/结果验真都不在这里（Workflow 的职责）；
 * 2. 工具别名统一映射到现有 Java 工具名（如 job_search → JobSearchTool.searchJobs），
 *    模型只看别名和 Schema，不接触真实类名；
 * 3. 终结工具（如 finish_job_search）也占一个白名单位——它只提交“提议”，
 *    是否成立由 Workflow 复核。
 *
 * @param agentName         Agent 名称（日志/追踪用）
 * @param systemPrompt      角色与输出契约的系统提示词
 * @param toolAliases       别名 → Java 工具名
 * @param toolSpecs         对模型暴露的工具定义（与白名单一致）
 * @param terminalToolName  终结工具别名
 * @param maxTurns          每 run 最大轮次
 * @param maxToolCalls      每 run 最多业务工具调用次数（终结工具不计）
 * @param deadlineSeconds   总截止时间（秒）
 */
public record AgentDefinition(
        String agentName,
        String systemPrompt,
        Map<String, String> toolAliases,
        List<AiToolSpec> toolSpecs,
        String terminalToolName,
        int maxTurns,
        int maxToolCalls,
        long deadlineSeconds
) {

    /** 第一阶段默认预算（文档给定初值）。 */
    public static final int DEFAULT_MAX_TURNS = 6;
    public static final int DEFAULT_MAX_TOOL_CALLS = 8;
    public static final long DEFAULT_DEADLINE_SECONDS = 180L;

    /** 该工具别名是否在白名单内。 */
    public boolean isAllowed(String alias) {
        return alias != null && toolAliases.containsKey(alias);
    }

    /** 别名 → Java 工具名。 */
    public String javaToolName(String alias) {
        return toolAliases.get(alias);
    }
}
