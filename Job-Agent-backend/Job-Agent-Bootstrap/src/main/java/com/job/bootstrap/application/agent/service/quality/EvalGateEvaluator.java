package com.job.bootstrap.application.agent.service.quality;

import com.job.common.vo.agent.AgentEvalRunVO;

/**
 * 功能: 基线对比与门禁判定（新批次 vs 基线批次）。
 *
 * <p>约定：返回 null 表示"尚未实现"，报告服务不展示门禁徽标；
 * 实现后返回 {@link GateResult} 即可自动接管。</p>
 */
public final class EvalGateEvaluator {

    // 通过率允许下降的百分点
    private static final double PASS_RATE_DROP_LIMIT = 2.0;

    // 工具准确率允许下降的百分点
    private static final double TOOL_DROP_LIMIT = 5.0;

    // 参数准确率允许下降的百分点。
    private static final double PARAM_DROP_LIMIT = 5.0;

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
     * @param current  当前批次（不能为空）
     * @param baseline 基线批次（可为空：首次评测没有基线）
     * @return 门禁结果；未实现时返回 null
     */
    public static GateResult evaluate(AgentEvalRunVO current, AgentEvalRunVO baseline) {

        if (current == null){
            return new GateResult(true,"没有可判定的批次",0,0,0);
        }

        if (baseline == null){
            return new GateResult(true,"首次评测，没有基线，本次不阻断",0,0,0);
        }

        double passDelta = diffRate(current.getPassCount(), current.getTotalCount(), baseline.getPassCount(), baseline.getTotalCount());
        double toolDelta = diff(current.getToolAccuracy(), baseline.getToolAccuracy());
        double paramDelta = diff(current.getParamAccuracy(), baseline.getParamAccuracy());

        // 任意一项跌破阈值即阻断；三个条件是"或"关系
        boolean blocked = passDelta < -PASS_RATE_DROP_LIMIT || toolDelta < -TOOL_DROP_LIMIT || paramDelta < -PARAM_DROP_LIMIT;

        String reason = blocked
                ? String.format("质量下降超过阈值：通过率 %+.1f%%、工具 %+.1f%%、参数 %+.1f%%", passDelta, toolDelta, paramDelta)
                : String.format("与基线持平或更好（通过率 %+.1f%%、工具 %+.1f%%、参数 %+.1f%%）", passDelta, toolDelta, paramDelta);


        return new GateResult(!blocked,reason,passDelta,toolDelta,paramDelta);
    }


    // 通过率差值：两边各自算百分比再相减，避免总数不同导致误判
    private static double diffRate(Integer currentPass, Integer currentTotal,
                                   Integer baselinePass, Integer baselineTotal) {
        double currentRate = currentTotal == null || currentTotal == 0
                ? 0 : currentPass * 100.0 / currentTotal;
        double baselineRate = baselineTotal == null || baselineTotal == 0
                ? 0 : baselinePass * 100.0 / baselineTotal;
        return round(currentRate - baselineRate);
    }

    // 数值型指标差值（空按 0 处理）
    private static double diff(Number current, Number baseline) {
        return round(value(current) - value(baseline));
    }

    //放0方法
    private static double value(Number number) {
        return number == null ? 0 : number.doubleValue();
    }

    // 统一保留 2 位，避免 0.30000000000000004 这种浮点噪声
    private static double round(double number) {
        return Math.round(number * 100) / 100.0;
    }
}
