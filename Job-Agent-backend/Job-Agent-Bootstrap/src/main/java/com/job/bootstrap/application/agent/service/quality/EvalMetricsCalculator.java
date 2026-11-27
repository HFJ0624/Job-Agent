package com.job.bootstrap.application.agent.service.quality;

import com.job.common.vo.agent.AgentEvalResultVO;

import java.util.List;

/**
 * 功能: 从逐用例结果重算整批指标（用于校验 run 上的聚合值是否失真）。
 *
 * <p>===== 练习 2：本类由你实现 =====</p>
 * <p>完整代码（含注释）、公式、单测用例见 docs/EVAL_CORE_CODE.md「练习 2」。</p>
 *
 * <p>约定：返回 null 表示"尚未实现"，报告服务会自动回退到 agent_eval_run 上的聚合值，
 * 因此现在页面仍可正常使用；实现后返回 {@link Metrics} 即可自动接管。</p>
 */
public final class EvalMetricsCalculator {

    private EvalMetricsCalculator() {
    }

    /**
     * 整批指标快照。
     *
     * @param passRate      通过率（0-100）
     * @param toolAccuracy  工具选择准确率（0-100，分母=有期望工具的用例）
     * @param paramAccuracy 参数准确率（0-100，分母=工具选对的用例）
     * @param ragHitRate    资料命中率（0-100，分母=期望了 RAG 的用例）
     * @param answerRate    答案达标率（0-100）
     * @param avgCostTime   平均耗时（ms）
     */
    public record Metrics(double passRate, double toolAccuracy, double paramAccuracy,
                          double ragHitRate, double answerRate, long avgCostTime) {
    }

    /**
     * 计算整批指标。
     *
     * <p>TODO(练习 2)：按 docs/EVAL_CORE_CODE.md 实现；当前返回 null 走回退逻辑。</p>
     *
     * @param cases 该批次全部逐用例结果（可能为空）
     * @return 指标；未实现时返回 null
     */
    public static Metrics calculate(List<AgentEvalResultVO> cases) {
        return null;
    }
}
