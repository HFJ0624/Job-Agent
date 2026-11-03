package com.job.bootstrap.agent.search;

import com.job.common.vo.preference.UserJobPreferenceVO;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SearchConstraints 单元测试（第一阶段）。
 *
 * 覆盖重点:
 * 1. 本轮显式条件优先于长期偏好，来源标记正确；
 * 2. 结果侧硬条件复核：城市不符/薪资不足/无法验证 → 不接受；
 * 3. 未设置硬条件时全部接受。
 */
class SearchConstraintsTest {

    private UserJobPreferenceVO preference() {
        UserJobPreferenceVO preference = new UserJobPreferenceVO();
        preference.setExpectedCity("上海");
        preference.setMinSalary(25);
        return preference;
    }

    private JobCandidates.JobCandidateItem item(String city, Integer maxSalary) {
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("jobTitle", "Java");
        raw.put("city", city);
        raw.put("maxSalary", maxSalary);
        return new JobCandidates.JobCandidateItem(1L, raw);
    }

    @Test
    void explicitShouldOverridePreference() {
        SearchConstraints constraints = SearchConstraints.merge(
                Map.of("city", "杭州"), preference());

        assertThat(constraints.city()).isEqualTo("杭州");
        assertThat(constraints.sources()).containsEntry("city", SearchConstraints.SOURCE_EXPLICIT);
        // 偏好里的 25 按 K 表达 → 统一为 25000 元/月
        assertThat(constraints.minSalary()).isEqualTo(25000);
        assertThat(constraints.sources()).containsEntry("minSalary", SearchConstraints.SOURCE_PREFERENCE);
    }

    @Test
    void resultAcceptShouldCheckCityAndSalary() {
        // 硬条件薪资下限 25000 元/月
        SearchConstraints constraints = SearchConstraints.merge(Map.of("city", "上海", "minSalary", 25000), null);

        assertThat(constraints.accepts(item("上海-浦东", 30000))).isTrue();
        assertThat(constraints.accepts(item("北京", 30000))).isFalse();
        assertThat(constraints.accepts(item("上海", 20000))).isFalse();
        // 无法验证（缺城市/缺薪资）→ 不接受
        assertThat(constraints.accepts(item(null, 30000))).isFalse();
        assertThat(constraints.accepts(item("上海", null))).isFalse();
    }

    @Test
    void noHardConditionShouldAcceptEverything() {
        SearchConstraints constraints = SearchConstraints.merge(Map.of(), null);

        assertThat(constraints.hasHardConstraints()).isFalse();
        assertThat(constraints.accepts(item(null, null))).isTrue();
    }

    @Test
    void preferenceShouldFillCityAndSalaryWhenNoExplicit() {
        SearchConstraints constraints = SearchConstraints.merge(Map.of("keyword", "Java"), preference());

        assertThat(constraints.keyword()).isEqualTo("Java");
        assertThat(constraints.city()).isEqualTo("上海");
        assertThat(constraints.minSalary()).isEqualTo(25000);
        assertThat(constraints.sources()).containsEntry("city", SearchConstraints.SOURCE_PREFERENCE);
    }
}
