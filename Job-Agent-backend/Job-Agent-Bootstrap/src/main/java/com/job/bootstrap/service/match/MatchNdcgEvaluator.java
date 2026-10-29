package com.job.bootstrap.service.match;

import java.util.List;

/**
 * 功能: 匹配排序评估器（任务 J5）。
 *
 * 解决什么问题:
 * 1. “匹配更准了”不能靠嘴说——需要基于标注样本的排序指标；
 * 2. 本组件提供 NDCG@K 与 Precision@K 两个纯函数，评估链路（标注集 + 排序对比测试）复用。
 *
 * 指标口径:
 * 1. NDCG@K = DCG@K / IDCG@K；DCG@K = Σ rel_i / log2(i+2)（i 从 1 开始，位置 1 分母 log2(2)=1）；
 * 2. 相关性分级 0-3（0 不相关，1 弱相关，2 相关，3 高度相关），与标注集一致；
 * 3. Precision@K = 前 K 中相关（rel>=2）的比例。
 */
public final class MatchNdcgEvaluator {

    private MatchNdcgEvaluator() {
    }

    /** 计算 NDCG@K。relevances 按排名顺序给出（第 1 个是 Top1）。 */
    public static double ndcgAtK(List<Integer> relevances, int k) {
        if (relevances == null || relevances.isEmpty() || k <= 0) {
            return 0d;
        }
        int limit = Math.min(k, relevances.size());
        double dcg = 0d;
        for (int i = 0; i < limit; i++) {
            dcg = dcg + gain(relevances.get(i)) / Math.log(i + 2) / Math.log(2);
        }
        double idcg = 0d;
        List<Integer> ideal = relevances.stream()
                .sorted((a, b) -> Integer.compare(b, a))
                .toList();
        for (int i = 0; i < limit; i++) {
            idcg = idcg + gain(ideal.get(i)) / Math.log(i + 2) / Math.log(2);
        }
        if (idcg <= 0d) {
            return 0d;
        }
        return dcg / idcg;
    }

    /** 计算 Precision@K：前 K 中相关（rel>=2）比例。 */
    public static double precisionAtK(List<Integer> relevances, int k) {
        if (relevances == null || relevances.isEmpty() || k <= 0) {
            return 0d;
        }
        int limit = Math.min(k, relevances.size());
        long relevant = relevances.subList(0, limit).stream()
                .filter(rel -> rel != null && rel >= 2)
                .count();
        return relevant * 1.0 / limit;
    }

    // 增益函数：线性增益（rel 本身），与标注分级 0-3 对齐
    private static double gain(Integer relevance) {
        return relevance == null || relevance <= 0 ? 0d : relevance.doubleValue();
    }
}
