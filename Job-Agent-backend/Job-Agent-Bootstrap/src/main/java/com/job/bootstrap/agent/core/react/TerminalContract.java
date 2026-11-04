package com.job.bootstrap.agent.core.react;

import com.job.bootstrap.ai.turn.AiToolSpec;

/**
 * 功能: 终结工具契约（第二阶段泛化）。
 *
 * 设计说明:
 * 1. 不同 Agent 的终结结果结构完全不同（搜索是候选 ID 列表、简历是画像、匹配是报告），
 *    这里用泛型契约隔离：每个 Agent 提供自己的工具定义 + 解析/业务约束校验 + 结论提取；
 * 2. 解析只做“结构化 + 第一层业务约束”，候选/证据/排名的真伪仍由 Workflow 复核；
 * 3. 旧 finish_job_search 保持原样，通过 {@code TerminalToolContract} 实现本接口。
 *
 * @param <T> 提议类型
 */
public interface TerminalContract<T> {

    /** 终结工具名（对模型暴露）。 */
    String toolName();

    /** 对模型暴露的工具定义。 */
    AiToolSpec spec();

    /**
     * 解析参数并做第一层校验。
     *
     * @return proposal 非空表示合法；error 非空表示需要模型修正
     */
    TerminalParseResult<T> parseTyped(String argumentsJson);

    /**
     * 从提议中提取业务结论码（如 FOUND/EMPTY/NEED_INPUT/FAILED/COMPLETED/PARTIAL）。
     * 注意：这是**业务结论**，与运行时原因码（预算/协议/模型）严格分离。
     */
    String outcomeOf(T proposal);

    /** 一句可读摘要（用于运行结果与拒绝原因，不直接作为对外答案）。 */
    default String summaryOf(T proposal) {
        return "";
    }

    /**
     * 解析结果。
     *
     * @param proposal 合法提议（可为 null）
     * @param error    错误文案（非空表示需要修正）
     */
    record TerminalParseResult<T>(T proposal, String error) {

        public static <T> TerminalParseResult<T> ok(T proposal) {
            return new TerminalParseResult<>(proposal, null);
        }

        public static <T> TerminalParseResult<T> error(String message) {
            return new TerminalParseResult<>(null, message);
        }
    }
}
