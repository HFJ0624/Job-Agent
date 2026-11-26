package com.job.bootstrap.service.match;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MatchNdcgEvaluator 单元测试（任务 J5）。
 *
 * 覆盖重点:
 * 1. 完美排序 NDCG=1；
 * 2. 逆序排序 NDCG 明显低于 1（可分辨）；
 * 3. k 大于列表长度时按列表长度计算；
 * 4. 全不相关返回 0；
 * 5. Precision@K 口径（rel>=2 记相关）。
 */
class MatchNdcgEvaluatorTest {

    @Test
    void perfectOrderShouldScoreOne() {
        double ndcg = MatchNdcgEvaluator.ndcgAtK(List.of(3, 3, 2, 2, 1, 0), 5);

        assertThat(ndcg).isCloseTo(1d, org.assertj.core.data.Offset.offset(0.0001));
    }

    @Test
    void reversedOrderShouldScoreLower() {
        double perfect = MatchNdcgEvaluator.ndcgAtK(List.of(3, 3, 2, 2, 1), 5);
        double reversed = MatchNdcgEvaluator.ndcgAtK(List.of(0, 1, 2, 2, 3), 5);

        assertThat(perfect).isCloseTo(1d, org.assertj.core.data.Offset.offset(0.0001));
        assertThat(reversed).isLessThan(0.8d);
    }

    @Test
    void kLargerThanListShouldUseListSize() {
        double ndcg = MatchNdcgEvaluator.ndcgAtK(List.of(3, 2), 5);

        assertThat(ndcg).isCloseTo(1d, org.assertj.core.data.Offset.offset(0.0001));
    }

    @Test
    void allIrrelevantShouldScoreZero() {
        assertThat(MatchNdcgEvaluator.ndcgAtK(List.of(0, 0, 0), 3)).isEqualTo(0d);
        assertThat(MatchNdcgEvaluator.ndcgAtK(List.of(), 3)).isEqualTo(0d);
        assertThat(MatchNdcgEvaluator.ndcgAtK(null, 3)).isEqualTo(0d);
    }

    @Test
    void precisionShouldCountRelevantAsTwoOrAbove() {
        assertThat(MatchNdcgEvaluator.precisionAtK(List.of(3, 2, 1, 0), 4)).isCloseTo(0.5d, org.assertj.core.data.Offset.offset(0.0001));
        assertThat(MatchNdcgEvaluator.precisionAtK(List.of(1, 0), 2)).isEqualTo(0d);
    }
}
