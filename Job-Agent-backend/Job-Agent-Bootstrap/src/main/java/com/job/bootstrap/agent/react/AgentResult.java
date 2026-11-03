package com.job.bootstrap.agent.react;

import java.util.List;

/**
 * 功能: Agent 运行结果（第一阶段 ReAct 内核）。
 *
 * 说明:
 * 1. outcome 与运行时原因码分离：outcome 是业务结论（模型提议+Workflow 复核后的语义），
 *    reasonCode 是运行时事实（BUDGET_TURNS_EXHAUSTED / BUDGET_TIMEOUT / PROTOCOL_ERROR /
 *    TOOL_ERROR / MODEL_ERROR），二者不能互相冒充；
 * 2. toolCalls/turns/elapsedMillis 供 Trace 与预算审计；
 * 3. candidateIds 只是“提议”——Workflow 必须回到真实工具结果重建候选后才能对外输出。
 *
 * @param outcome        FOUND / EMPTY / NEED_INPUT / FAILED
 * @param candidateIds   候选 ID 提议（FOUND 时非空；Workflow 复核前不可信）
 * @param missingFields  缺失字段（NEED_INPUT）
 * @param question       向用户提出的问题（NEED_INPUT）
 * @param reasonCode     运行时原因码（可空）
 * @param summary        简短说明
 * @param toolCallsUsed  业务工具调用次数
 * @param turnsUsed      轮次
 * @param elapsedMillis  总耗时
 */
public record AgentResult(
        Outcome outcome,
        List<Long> candidateIds,
        List<String> missingFields,
        String question,
        String reasonCode,
        String summary,
        int toolCallsUsed,
        int turnsUsed,
        long elapsedMillis
) {

    public enum Outcome {
        FOUND, EMPTY, NEED_INPUT, FAILED
    }

    /** 运行时原因码（文档要求：超时/次数耗尽/模型异常由运行时产生，不能只依赖模型自停）。 */
    public static final class ReasonCode {
        public static final String BUDGET_TURNS_EXHAUSTED = "BUDGET_TURNS_EXHAUSTED";
        public static final String BUDGET_TOOL_CALLS_EXHAUSTED = "BUDGET_TOOL_CALLS_EXHAUSTED";
        public static final String BUDGET_TIMEOUT = "BUDGET_TIMEOUT";
        public static final String PROTOCOL_ERROR = "PROTOCOL_ERROR";
        public static final String MODEL_ERROR = "MODEL_ERROR";
        public static final String TOOL_ERROR = "TOOL_ERROR";
        /** 父任务模型请求额度用尽（第三阶段）。 */
        public static final String BUDGET_MODEL_CALLS_EXHAUSTED = "BUDGET_MODEL_CALLS_EXHAUSTED";
        /** 用户显式取消（第三阶段）。 */
        public static final String TASK_CANCELLED = "TASK_CANCELLED";

        private ReasonCode() {
        }
    }

    public static AgentResult failed(String reasonCode, String summary, int toolCalls, int turns, long elapsedMillis) {
        return new AgentResult(Outcome.FAILED, List.of(), List.of(), null, reasonCode, summary,
                toolCalls, turns, elapsedMillis);
    }

    public boolean succeeded() {
        return outcome == Outcome.FOUND || outcome == Outcome.EMPTY || outcome == Outcome.NEED_INPUT;
    }
}
