package com.job.bootstrap.application.agent.service.quality;

import com.job.common.vo.agent.AgentEvalResultVO;
import org.springframework.util.StringUtils;

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
     * @param cases 该批次全部逐用例结果（可能为空）
     * @return 指标；未实现时返回 null
     */
    public static Metrics calculate(List<AgentEvalResultVO> cases) {

        //判断为空直接返回0
        if (cases == null || cases.isEmpty()){
            return new Metrics(0,0,0,0,0,0);
        }

        int total =cases.size();
        int pass = 0;

        //三个准确率各自的分子/分母
        int toolBase = 0,toolPass = 0;
        int paramBase = 0,paramPass = 0;
        int ragBase = 0,ragPass = 0;
        int answerPass = 0;

        int cost = 0;

        for (AgentEvalResultVO item : cases){

            if (item == null){
                continue;
            }

            if (Integer.valueOf(1).equals(item.getPassStatus())){
                pass++;
            }

            //计算工具名称指标
            if (StringUtils.hasText(item.getExpectedToolName())) {
                toolBase++;
                if (Integer.valueOf(1).equals(item.getToolSelectPass())){
                    toolPass++;
                }
            }

            // 计算工具选择指标
            if (Integer.valueOf(1).equals(item.getToolSelectPass())) {
                paramBase++;
                if (Integer.valueOf(1).equals(item.getToolParamPass())) {
                    paramPass++;
                }
            }

            //计算rag指标
            if (item.getExpectedRagDocumentId() != null) {
                ragBase++;
                if (Integer.valueOf(1).equals(item.getRagHitPass())) {
                    ragPass++;
                }
            }

            //计算回答指标
            if (Integer.valueOf(1).equals(item.getAnswerKeywordPass())) {
                answerPass++;
            }

            //计算成本
            cost += item.getCostTime() == null ? 0 : item.getCostTime();
        }

        //返回百分比指标数据
        return new Metrics(
                percent(pass,total),
                percent(toolBase,toolBase),
                percent(paramPass,paramBase),
                percent(ragPass,ragBase),
                percent(answerPass,total),
                cost / total
        );
    }

    /**
     * 百分比：保留 2 位；分母为 0 返回 0，避免 NaN 传到前端。
     */
    private static double percent(int numerator,int denominator){
        if (denominator == 0){
            return 0;
        }

        return Math.round(numerator * 10000.0 / denominator) / 100.0;
    }
}
