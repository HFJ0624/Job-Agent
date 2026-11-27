package com.job.bootstrap.application.agent.service.quality;

import com.job.common.vo.agent.AgentEvalRunVO;

/**
 * 功能: 基线对比与门禁判定（新批次 vs 基线批次）。
 *
 * <p>===== 练习 3：本类由你实现 =====</p>
 * <p>完整代码（含注释）、阈值规则、单测用例见 docs/EVAL_CORE_CODE.md「练习 3」。</p>
 *
 * <p>约定：返回 null 表示"尚未实现"，报告服务不展示门禁徽标；
 * 实现后返回 {@link GateResult} 即可自动接管。</p>
 */
public final class EvalGateEvaluator {

    private EvalGateEvaluator() {
    }

    /**
     * 门禁结果。
     *
     * @param pass          是否允许（true=PASS，false=BLOCK）
     * @param reason        给非技术用户看的一句话结论
     * @param passRateDelta 通过率差值（百分点，正数更好）
     * @param toolDelta     工具准确率差值
     * @param paramDelta    参数准确率差值
     */
    public record GateResult(boolean pass, String reason,
                             double passRateDelta, double toolDelta, double paramDelta) {
    }

    /**
     * 对比当前批次与基线批次。
     *
     * <p>TODO(练习 3)：按 docs/EVAL_CORE_CODE.md 实现；当前返回 null 表示不做门禁。</p>
     *
     * @param current  当前批次（不能为空）
     * @param baseline 基线批次（可为空：首次评测没有基线）
     * @return 门禁结果；未实现时返回 null
     */
    public static GateResult evaluate(AgentEvalRunVO current, AgentEvalRunVO baseline) {
        return null;
    }
}
